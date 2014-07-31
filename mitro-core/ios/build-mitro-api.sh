# Script for building openssl for ios
#
# Usage: build-openssl src_dir dst_dir
#

set -e

if [[ -z "$1" ]] || [[ -z "$2" ]]; then
  echo "Usage: build-mitro-api src_dir dst_dir"
  exit 1
fi

SRC_DIR="$1"
DST_DIR="$2"

MITRO_API_SRC_DIR="${SRC_DIR}/mitro-api"

cp -Rf ../cpp/net ${MITRO_API_SRC_DIR}/net
cp -Rf ../cpp/mitro_api ${MITRO_API_SRC_DIR}/mitro_api

echo "Building mitro-api for ${PLATFORM}${SDK_VERSION}"
pushd "${MITRO_API_SRC_DIR}"

make
make install
popd
