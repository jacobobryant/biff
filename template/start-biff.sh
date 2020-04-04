#!/bin/bash
cd "$(dirname "${BASH_SOURCE[0]}")"
while clojure -m biff.core; do sleep 1; done
