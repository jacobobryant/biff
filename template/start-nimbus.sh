#!/bin/bash
while clj -m nimbus.core; do sleep 1; done
