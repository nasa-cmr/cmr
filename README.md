# cmr-search-app

FIXME

## TODO

  * Draw informal design for this application
  * Test against indexed collections as indexed by catalog-rest
    * This will be a way to make it work before other components are complete.
    * Use curl requests to send searches.


## Example CURL requests


### Find all collections
```
curl -H "Accept: application/json" -i "http://localhost:3000/collections"
```

### Find all collections with a bad parameter
```
curl -H "Accept: application/json" -i "http://localhost:3000/collections?foo=5"
```

### Find all collections with a dataset id
```
curl -H "Accept: application/json" -i "http://localhost:3000/collections?dataset_id\[\]=DatasetId%204"
```

### Find all collections with multiple dataset ids
```
curl -H "Accept: application/json" -i "http://localhost:3000/collections?dataset_id\[\]=DatasetId%204&dataset_id\[\]=DatasetId%205"
```

### Find as XML
TODO implement support for retrieving in XML.
Also make sure enough information is returned that Catalog-REST can work.
```
curl -H "Accept: application/xml" -i "http://localhost:3000/collections"
```


## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein ring server

## License

Copyright © 2014 NASA
