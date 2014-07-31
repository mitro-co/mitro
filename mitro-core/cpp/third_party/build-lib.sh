set -e

if [[ -z "$1" ]]; then
  echo "Usage: build-gtest src_dir dst_dir"
  exit 1
fi

BUILD_SCRIPT=$1

ROOT_DIR=$( cd "$( dirname "$0" )" && pwd )
SRC_DIR="${ROOT_DIR}/src"
DST_DIR="${ROOT_DIR}"

mkdir -p ${SRC_DIR}

export TOOLS_DIR=../../tools
export PATCH_DIR=${ROOT_DIR}
export PLATFORM=darwin

${ROOT_DIR}/${BUILD_SCRIPT} "${SRC_DIR}" "${DST_DIR}"
