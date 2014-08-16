#!/bin/bash

# curl -XDELETE 'http://localhost:9200/ircbot/'

curl -XPOST "http://localhost:9200/ircbot_v3" -d '{
  "settings" : {
    "number_of_shards" : 1
  },
  "mappings": {
    "message": {
      "_timestamp": {
        "enabled": true,
        "path": "time"
      },
      "properties": {
        "linkCount": {
          "type": "long"
        },
        "links": {
          "type": "string",
          "index": "not_analyzed"
        },
        "nickname": {
          "type": "string"
        },
        "text": {
          "type": "string"
        },
        "time": {
          "type": "date"
        },
        "username": {
          "type": "string",
          "index": "not_analyzed"
        }
      }
    }
  }
}' && echo

# Add alias
curl -XPUT "http://localhost:9200/ircbot_v3/_alias/ircbot"
# Delete old alias
curl -XDELETE 'http://localhost:9200/ircbot_v2/_alias/ircbot'

# Delete old index (if exists)
curl -XDELETE "http://localhost:9200/ircbot_v2/"
