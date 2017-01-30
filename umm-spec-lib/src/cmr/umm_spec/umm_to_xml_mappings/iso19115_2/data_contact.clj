(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2.data-contact
  "Functions for generating ISO19115-2 XML elements from UMM DataCenters, ContactPersons and ContactGroups."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.xml.gen :refer :all]
   [cmr.umm-spec.iso19115-2-util :as iso]
   [cmr.umm-spec.util :refer [char-string]]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.data-contact :as data-contact]
   [cmr.umm-spec.umm-to-xml-mappings.iso19115-2.distributions-related-url :as related-url]))

(def translated-contact-mechanism-types
 {"Direct Line" "Telephone"
  "Email" "Email"
  "Fax" "Fax"
  "Mobile" "Telephone"
  "Primary" "Telephone"
  "TDD/TTY Phone" "Telephone"
  "Telephone" "Telephone"
  "U.S. toll free" "Telephone"})

(def data-center-role->iso-role
 (set/map-invert data-contact/iso-data-center-role->umm-role))

(defn- get-phone-contact-mechanisms
 "Get phone/fax contact mechanisms from contact info"
 [contact-info]
 (when-let [contact-mechanisms (:ContactMechanisms contact-info)]
  (let [contact-mechanisms
        (map #(assoc % :Type (get translated-contact-mechanism-types (:Type %)))
             (:ContactMechanisms contact-info))]
   (filter #(or (= "Telephone" (:Type %))
                (= "Fax" (:Type %)))
           contact-mechanisms))))

(defn generate-contact-info
 "Generate contact info xml from ContactInformation"
 [contact-info]
 [:gmd:contactInfo
  [:gmd:CI_Contact
   (when-let [phone-contacts (seq (get-phone-contact-mechanisms contact-info))]
     [:gmd:phone
      [:gmd:CI_Telephone
       (for [phone (filter #(= "Telephone" (:Type %)) phone-contacts)]
        [:gmd:voice (char-string (:Value phone))])
       (for [fax (filter #(= "Fax" (:Type %)) phone-contacts)]
        [:gmd:facsimile (char-string (:Value fax))])]])
   (let [address (first (:Addresses contact-info))
         emails (filter #(= "Email" (:Type %)) (:ContactMechanisms contact-info))]
     (when (or address emails)
      [:gmd:address
       [:gmd:CI_Address
        (for [street-address (:StreetAddresses address)]
         [:gmd:deliveryPoint (char-string street-address)])
        (when-let [city (:City address)]
         [:gmd:city (char-string city)])
        (when-let [state (:StateProvince address)]
         [:gmd:administrativeArea (char-string state)])
        (when-let [postal-code (:PostalCode address)]
         [:gmd:postalCode (char-string postal-code)])
        (when-let [country (:Country address)]
         [:gmd:country (char-string country)])
        (for [email emails]
         [:gmd:electronicMailAddress (char-string (:Value email))])]]))
   (when-let [url (first (:RelatedUrls contact-info))]
     (first
      (related-url/generate-online-resource-url
       (update url :URLs #(take 1 %))
       :gmd:onlineResource)))
   (when-let [hours (:ServiceHours contact-info)]
    [:gmd:hoursOfService (char-string hours)])
   (when-let [instruction (:ContactInstruction contact-info)]
    [:gmd:contactInstructions (char-string instruction)])]])

(defn- generate-data-center-name
 [data-center]
 (if (:LongName data-center)
  (str (:ShortName data-center) " &gt; " (:LongName data-center))
  (:ShortName data-center)))

(defn- generate-data-center
 "Generate data center XML for the data center and ISO role"
 [data-center iso-role]
 [:gmd:CI_ResponsibleParty
  [:gmd:organisationName
   (char-string (generate-data-center-name data-center))]
  (generate-contact-info (:ContactInformation data-center))
  [:gmd:role
   [:gmd:CI_RoleCode
     {:codeList (:ndgc iso/code-lists)
      :codeListValue iso-role} iso-role]]])

(defn- filter-data-centers-by-role
 [data-centers role]
 (filter #(some #{role} (:Roles %)) data-centers))

(defn generate-processing-centers
 [data-centers]
 (let [processors (filter-data-centers-by-role data-centers "PROCESSOR")]
  (seq
   (for [processor processors]
    [:gmd:processor
     (generate-data-center processor "processor")]))))

(defn generate-distributors
 "Distributors are incuded with data centers but also in
 /gmi:MI_Metadata/gmd:distributionInfo/gmd:MD_Distribution/gmd:distributor/gmd:MD_Distributor/gmd:distributorContact"
 [data-centers]
 (let [distributors (filter-data-centers-by-role data-centers "DISTRIBUTOR")]
  (seq
   (for [center distributors]
    [:gmd:distributor
     [:gmd:MD_Distributor
      [:gmd:distributorContact
       (generate-data-center center "distributor")]]]))))

(defn generate-archive-centers
 "Archive centers are included with the data centers but also in
 /gmi:MI_Metadata/:gmd:contact/gmd:CI_ResponsibleParty"
 [data-centers]
 (let [archive-centers (filter-data-centers-by-role data-centers "ARCHIVER")]
  (seq
   (for [center archive-centers]
    [:gmd:contact
     (generate-data-center center "custodian")]))))

(defn- generate-contact-person
 [person role data-center-name]
 (let [{:keys [FirstName MiddleName LastName NonDataCenterAffiliation ContactInformation]} person]
  [:gmd:pointOfContact
   [:gmd:CI_ResponsibleParty
    [:gmd:individualName (char-string (str/trim (str/join " " [FirstName MiddleName LastName])))]
    (when data-center-name
     [:gmd:organisationName (char-string data-center-name)])
    [:gmd:positionName (char-string NonDataCenterAffiliation)]
    (generate-contact-info ContactInformation)
    [:gmd:role
     [:gmd:CI_RoleCode
       {:codeList (:ndgc iso/code-lists)
        :codeListValue "pointOfContact"} "pointOfContact"]]]]))

(defn generate-data-centers
 "Generate data center XML from DataCenters"
 [data-centers]
 (for [data-center data-centers
       role (:Roles data-center)
       :let [iso-role (get data-center-role->iso-role role)]]
   [:gmd:pointOfContact
     (generate-data-center data-center iso-role)]))

(defn generate-data-center-contact-persons
 [data-centers]
 (for [data-center data-centers]
  (map #(generate-contact-person % "Data Center Contact" (generate-data-center-name data-center))
       (:ContactPersons data-center))))
