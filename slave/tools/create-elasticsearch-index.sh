#!/bin/bash

# curl -XDELETE 'http://localhost:9200/ircbot/'

curl -XPOST "http://localhost:9200/ircbot" -d '{
  "settings" : {
    "number_of_shards" : 1
  },
  "mappings" : {
    "message" : {
      "_timestamp" : {
        "enabled" : true,
        "path" : "time"
      }
    }
  }
}' && echo
