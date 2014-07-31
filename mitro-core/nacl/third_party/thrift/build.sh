#export LIBS="-lnacl_io -lpthread"
export INCLUDES=-I./src

EXTRA_CONFIGURE_ARGS="--with-cpp=yes --with-c_glib=no --with-csharp=no --with-d=no --with-erlang=no --with-haskell=no --with-go=no --with-java=no --with-perl=no --with-php=no --with-php_extension=no --with-python=no --with-ruby=no"
EXTRA_CONFIGURE_ARGS+=" --with-boost=${NACLPORTS_PREFIX} --with-libevent=no --with-zlib=no --with-tests=no --enable-static=yes"
