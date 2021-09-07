#!/usr/bin/env sh

sleep 15
echo "Setup kafka-adminrest by appyling oneshot file"
curl -X PUT "http://igroup:itest@kafkadminrest:8080/api/v1/oneshot" -H  "Accept: application/json" -H  "Content-Type: application/json" --data "@/usr/local/bin/oneshot.json" -v
