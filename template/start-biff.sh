#!/bin/bash
while clj -m biff.core; do sleep 1; done
