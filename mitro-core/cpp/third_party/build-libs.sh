# Script for building Mitro iOS libraries.
#

set -e

ROOT_DIR=$( cd "$( dirname "$0" )" && pwd )
SRC_DIR="${ROOT_DIR}/src"
DST_DIR="${ROOT_DIR}"

mkdir -p ${SRC_DIR}

export TOOLS_DIR=../../tools
export PATCH_DIR=${ROOT_DIR}
export PLATFORM=darwin

${ROOT_DIR}/build-gtest.sh "${SRC_DIR}" "${DST_DIR}"
${ROOT_DIR}/build-keyczar.sh "${SRC_DIR}" "${DST_DIR}"
${ROOT_DIR}/build-boost.sh "${SRC_DIR}" "${DST_DIR}"
${ROOT_DIR}/build-thrift.sh "${SRC_DIR}" "${DST_DIR}"
