#!/bin/sh
set -eu

for bucket in "$STAGING_STORAGE_BUCKET" "$PERMANENT_STORAGE_BUCKET"; do
  if ! awslocal s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
    awslocal s3api create-bucket --bucket "$bucket" >/dev/null
  fi
done
