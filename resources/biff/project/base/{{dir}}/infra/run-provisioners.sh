#!/bin/bash
# Managed by Biff
set -e
for f in /tmp/provisioners/*; do
  source $f
done
