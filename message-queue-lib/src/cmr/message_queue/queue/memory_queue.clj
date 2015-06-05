(ns cmr.message-queue.queue.memory-queue
  "Defines an in memory implementation of the Queue protocol. It uses core.async for message passing."
  (:require [clojure.core.async :as a]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.message-queue.config :as config]
            [cmr.common.util :as u]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))

(def CHANNEL_BUFFER_SIZE
  "The number of messages that can be placed on a channel before the caller will block."
  10)

(defn- attempt-retry
  "Attempts to retry processing the message unless the retry count has been exceeded."
  [queue-broker queue-name msg resp]
  (let [retry-count (get msg :retry-count 0)]
    (if (queue/retry-limit-met? msg (count (config/rabbit-mq-ttls)))
      ;; give up
      (warn "Max retries exceeded for processing message:" (pr-str msg))
      (let [new-retry-count (inc retry-count)
            msg (assoc msg :retry-count new-retry-count)]
        (info "Message" (pr-str msg) "re-queued with response:" (pr-str (:message resp)))
        (info (format "Retrying with retry-count =%d" new-retry-count))
        (queue/publish-to-queue queue-broker queue-name msg)))))

(defn- create-async-handler
  "Creates a go block that will asynchronously pull messages off the queue, pass them to the handler,
  and process the response."
  [queue-broker queue-name handler]
  (let [queue-ch (get-in queue-broker [:queues-to-channels queue-name])]
    (a/go
      (try
        (u/while-let
          [msg (a/<! queue-ch)]
          (try
            (let [resp (handler msg)]
              (case (:status resp)
                :success nil
                :retry (attempt-retry queue-broker queue-name msg resp)
                :failure (error (format (str "Message failed processing with error '%s', it has been "
                                             "removed from the message queue. Message details: %s")
                                        (:message resp) msg))))
            (catch Throwable e
              (error "Message processing failed for message" (pr-str msg) "with error:"
                     (.getMessage e))
              ;; Retry by requeueing the message
              (attempt-retry queue-broker queue-name msg {:message (.getMessage e)}))))
        (finally
          (info "Async go handler for queue" queue-name "completing."))))))

(defn drain-channels
  "Removes all messages from the given channels. Will not block"
  [channels]
  (loop []
    (when-not (= :done (first (a/alts!! (vec channels) :default :done)))
      (recur))))

(defrecord MemoryQueueBroker
  [
   ;; A list of queue names
   queues

   ;; A map of exchange names to sets of queue names to which the exchange will broadcast.
   exchanges-to-queue-sets

   ;; A map of queue names to core async channels containing messages to deliver
   queues-to-channels

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Running State

   ;; An atom containing a sequence of channels returned by the go block processors for each handler.
   handler-channels-atom
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    this)

  (stop
    [this system]
    (drain-channels (vals queues-to-channels))

    ;; Wait for go blocks to finish
    (doseq [handler-ch @handler-channels-atom]
      (a/close! handler-ch)
      (let [[_ ch] (a/alts!! [(a/timeout 2000)
                              handler-ch])]
        (when-not (= ch handler-ch)
          (warn "Timed out waiting for go block to complete"))))
    (reset! handler-channels-atom nil)

    this)
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  queue/Queue

  (publish-to-queue
    [this queue-name msg]
    ;; Puts the message on the channel
    (a/>!! (queues-to-channels queue-name) msg))

  (publish-to-exchange
    [this exchange-name msg]
    (every? #(queue/publish-to-queue this % msg)
            (exchanges-to-queue-sets exchange-name)))

  (subscribe
    [this queue-name handler]
    (swap! handler-channels-atom conj (create-async-handler this queue-name handler))
    nil)

  (reset
    [this]
    ;; clear all channels
    (drain-channels (vals queues-to-channels)))

  (health
    [this]
    {:ok? true}))
(record-pretty-printer/enable-record-pretty-printing MemoryQueueBroker)

(defn create-memory-queue-broker
  "Creates a memory queue with the given parameters. This should match the same parameters of the
  RabbitMQBroker"
  [{:keys [queues exchanges queues-to-exchanges]}]
  (let [exchanges-to-empty-sets (into {} (for [e exchanges] [e #{}]))
        exchanges-to-queue-sets (reduce (fn [e-to-q [queue exchange]]
                                          (update-in e-to-q [exchange] conj queue))
                                        exchanges-to-empty-sets
                                        queues-to-exchanges)
        q-to-chans (into {} (for [q queues] [q (a/chan CHANNEL_BUFFER_SIZE)]))]
    (->MemoryQueueBroker queues exchanges-to-queue-sets
                         q-to-chans
                         (atom nil))))


(comment

  (def qb (create-memory-queue-broker {:queues ["a" "b" "c"]
                                       :exchanges ["e1" "e2"]
                                       :queues-to-exchanges {"a" "e1", "b" "e1", "c" "e2"}}))

  (def running-qb (lifecycle/start qb nil))

  (def stopped-qb (lifecycle/stop running-qb nil))

  (do
    (defn message-handler
      [queue-name msg]
      (println "Handling" (pr-str msg) "from queue" queue-name)
      {:status :success})

    (queue/subscribe running-qb "a" (partial message-handler "a"))
    (queue/subscribe running-qb "b" (partial message-handler "b"))
    (queue/subscribe running-qb "c" (partial message-handler "c")))

  (queue/publish-to-queue running-qb "a" {:id 1})
  (queue/publish-to-queue running-qb "b" {:id 2})
  (queue/publish-to-queue running-qb "c" {:id 3})

  (queue/publish-to-exchange running-qb "e1" {:id 4})
  (queue/publish-to-exchange running-qb "e2" {:id 5})





  )