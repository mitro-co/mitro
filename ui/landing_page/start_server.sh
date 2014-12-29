#!/bin/bash
echo "serving http://localhost:8000/"
cd build && python -mSimpleHTTPServer
