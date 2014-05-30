# cmr-bootstrap-app

Bootstrap is a CMR application that can bootstrap the CMR with data from Catalog REST. It has API methods for copying data from Catalog REST to the metadata db. It can also bulk index everything in the Metadata DB.

## Running

To start a web server for the application, run:

    lein run


## Example curls

### bulk copy provider FIX_PROV1 and all it's collections and granules to the metadata db

	curl -XPOST  -H "Content-Type: application/json" -d '{"provider_id": "FIX_PROV1"}' http://localhost:3006/bulk_migration/providers

For the echo-reverb test fixture data, the following curl can be used to check metadata db
to make sure the new data is available:

	curl -v http://localhost:3001/concepts/G1000000033-FIX_PROV1

This should return the granule including the echo-10 xml.



## License

Copyright © 2014 NASA
