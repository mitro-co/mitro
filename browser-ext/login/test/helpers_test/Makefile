#
# Makefile to build the helpers test extensions
#
#
CFX=$(shell pwd)/../../../third_party/addon-sdk-1.14/bin/cfx

ifeq ($(shell uname),Darwin)
  GOOGLE_CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
else
  GOOGLE_CHROME=google-chrome
endif

BUILD_DIR:=$(shell pwd)/build
STATIC_SERVER_DIR=$(BUILD_DIR)/static

EXTENSION_DIR=$(shell pwd)/../..
SCRIPTS_EXTRACTOR=$(EXTENSION_DIR)/extract_scripts.py

CHROME_BUILD_DIR=$(BUILD_DIR)/chrome
SAFARI_BUILD_DIR=$(BUILD_DIR)/safari.safariextension
FIREFOX_BUILD_DIR=$(BUILD_DIR)/firefox
SUBDIRS=html js css
CHROME_SUBDIRS=$(foreach sub,$(SUBDIRS),$(CHROME_RELEASE_DIR)/$(sub))
FIREFOX_SUBDIRS=$(FIREFOX_BUILD_DIR)/lib $(FIREFOX_BUILD_DIR)/data
CHROME_RELEASE_SUBDIRS=$(foreach sub,$(SUBDIRS),$(CHROME_RELEASE_DIR)/$(sub))

# Assigns background and content scripts
# in a browser-specific way to safari/chrome extension
# using common jsons declaration
ASSIGN_SCRIPTS=$(EXTENSION_DIR)/assign_scripts.py

COMMON_SRCS=common/*.*
COMMON_JS_SRCS=common/js/*.js
HTML_SRCS=common/html/*.html
CSS_SRCS=common/css/*.css
SAFARI_SRCS=safari/*.*
FIREFOX_SRCS=firefox/*.*

# scripts_extraction_test.html requires a separate processing
STATIC_SERVER_FILES=$(filter-out %/scripts_extraction_test.html,$(wildcard common/static_server/*.*))

FIREFOX_DATA_SRCS=$(filter-out %/package.json %/main.js,$(wildcard $(FIREFOX_SRCS)))

STATIC_SRCS=$(EXTENSION_DIR)/frontend/static/js/*.* $(EXTENSION_DIR)/frontend/static/img/*.* $(EXTENSION_DIR)/common/*.*
STATIC_FILES=%/client.js %/jquery.min.js %/utils.js %/URI.js %/mitro_logo-19.png %/mitro_logo-38.png
TARGET_BASENAME=mitro-login-helpers-test

STATIC_RESULTS=$(filter $(STATIC_FILES), $(wildcard $(STATIC_SRCS)))

COMMON_DEPS= \
  $(foreach file,$(wildcard $(STATIC_SERVER_FILES)),$(STATIC_SERVER_DIR)/$(notdir $(file))) \
  $(STATIC_SERVER_DIR)/scripts_extraction_test.html


CHROME_DEPS= \
  $(foreach file,$(wildcard $(COMMON_JS_SRCS) $(STATIC_RESULTS) \
			$(COMMON_SRCS)),$(CHROME_BUILD_DIR)/$(notdir $(file))) \
  $(foreach file,$(wildcard $(HTML_SRCS)),$(CHROME_BUILD_DIR)/html/$(notdir $(file))) \
  $(foreach file,$(wildcard $(CSS_SRCS)),$(CHROME_BUILD_DIR)/$(notdir $(file))) \
  $(CHROME_BUILD_DIR)/helpers.js \
  $(CHROME_BUILD_DIR)/manifest.json


SAFARI_DEPS= \
  $(foreach file,$(filter-out %/Info.plist,$(wildcard $(COMMON_JS_SRCS) $(STATIC_RESULTS) \
			$(SAFARI_SRCS) $(COMMON_SRCS))),$(SAFARI_BUILD_DIR)/$(notdir $(file))) \
  $(foreach file,$(wildcard $(HTML_SRCS)),$(SAFARI_BUILD_DIR)/html/$(notdir $(file))) \
  $(foreach file,$(wildcard $(CSS_SRCS)),$(SAFARI_BUILD_DIR)/$(notdir $(file))) \
  $(SAFARI_BUILD_DIR)/helpers.js \
  $(SAFARI_BUILD_DIR)/mitro_logo_safari.png \
  $(SAFARI_BUILD_DIR)/Info.plist

FIREFOX_DEPS= \
  $(foreach file,$(wildcard $(COMMON_JS_SRCS) $(STATIC_RESULTS)\
			$(FIREFOX_DATA_SRCS) $(COMMON_SRCS)),$(FIREFOX_BUILD_DIR)/data/$(notdir $(file))) \
  $(foreach file,$(wildcard $(HTML_SRCS)),$(FIREFOX_BUILD_DIR)/data/html/$(notdir $(file))) \
  $(foreach file,$(wildcard $(CSS_SRCS)),$(FIREFOX_BUILD_DIR)/data/$(notdir $(file))) \
  $(FIREFOX_BUILD_DIR)/data/helpers.js \
  $(FIREFOX_BUILD_DIR)/lib/main.js \
  $(FIREFOX_BUILD_DIR)/lib/client.js \
  $(FIREFOX_BUILD_DIR)/lib/utils.js \
  $(FIREFOX_BUILD_DIR)/package.json \
  $(FIREFOX_BUILD_DIR)/data/paths.json

define MKDIR_RULE
$(1):
	mkdir -p $(1)
endef

define COPY_RULE
$(2): $(1) | $(dir $(2))
	@echo $(2)
	@cp $(1) $(2)
endef

define EXTRACT_SCRIPTS_RULE
$(2): $(1) | $(dir $(2))
	@echo $(SCRIPTS_EXTRACTOR) $(1) $(2)
	@$(SCRIPTS_EXTRACTOR) $(1) $(2)
endef

.PHONY: all chrome safari firefox test
all: chrome safari firefox
chrome: $(BUILD_DIR)/chrome.crx
firefox: $(BUILD_DIR)/firefox.xpi
safari: $(BUILD_DIR)/safari.safariextz

clean:
	rm -rf $(BUILD_DIR)

test: all
	./runtests.sh chrome,firefox,safari

$(info $(FIREFOX_SUBDIRS))
# Directories
$(foreach d,$(BUILD_DIR) $(CHROME_BUILD_DIR) $(CHROME_BUILD_DIR)/html $(SAFARI_BUILD_DIR) $(SAFARI_BUILD_DIR)/html $(FIREFOX_BUILD_DIR) $(FIREFOX_BUILD_DIR)/lib $(FIREFOX_BUILD_DIR)/data $(FIREFOX_BUILD_DIR)/data/html $(STATIC_SERVER_DIR) $(STATIC_SERVER_DIR)/html,$(eval $(call MKDIR_RULE,$(d))))

# CHROME_SRCS
$(foreach file,$(wildcard $(COMMON_JS_SRCS) $(STATIC_RESULTS) $(COMMON_SRCS) $(CSS_SRCS)),$(eval $(call COPY_RULE,$(file),$(CHROME_BUILD_DIR)/$(notdir $(file)))))
$(foreach file,$(wildcard $(HTML_SRCS)),$(eval $(call COPY_RULE,$(file),$(CHROME_BUILD_DIR)/html/$(notdir $(file)))))

# SAFARI_SRCS
$(foreach file,$(filter-out %/Info.plist, $(wildcard $(COMMON_JS_SRCS) $(STATIC_RESULTS) $(SAFARI_SRCS) $(COMMON_SRCS) $(CSS_SRCS))),$(eval $(call COPY_RULE,$(file),$(SAFARI_BUILD_DIR)/$(notdir $(file)))))
$(foreach file,$(wildcard $(HTML_SRCS)),$(eval $(call COPY_RULE,$(file),$(SAFARI_BUILD_DIR)/html/$(notdir $(file)))))

# FIREFOX_SRCS
$(foreach file,$(wildcard $(COMMON_JS_SRCS) $(STATIC_RESULTS) $(HTML_SRCS) $(FIREFOX_DATA_SRCS) $(COMMON_SRCS) $(CSS_SRCS)),$(eval $(call COPY_RULE,$(file),$(FIREFOX_BUILD_DIR)/data/$(notdir $(file)))))
$(foreach file,$(wildcard $(HTML_SRCS)),$(eval $(call EXTRACT_SCRIPTS_RULE,$(file),$(FIREFOX_BUILD_DIR)/data/html/$(notdir $(file)))))

# STATIC_SERVER_FILES
$(foreach file,$(wildcard $(STATIC_SERVER_FILES)),$(eval $(call COPY_RULE,$(file),$(STATIC_SERVER_DIR)/$(notdir $(file)))))

# Needed for testing extension with selenium.
# If we use decide to pack the extension for the web store, we'll want to
# save the key.
$(BUILD_DIR)/chrome.crx: $(COMMON_DEPS) $(CHROME_DEPS)
	$(GOOGLE_CHROME) --pack-extension=$(CHROME_BUILD_DIR)
	rm -f $(BUILD_DIR)/chrome.pem

$(BUILD_DIR)/firefox.xpi: $(COMMON_DEPS) $(FIREFOX_DEPS)
	cd $(BUILD_DIR) && \
	$(CFX) xpi --pkgdir=firefox && \
	mv mitro-helpers-test.xpi firefox.xpi

$(BUILD_DIR)/safari.safariextz: $(COMMON_DEPS) $(SAFARI_DEPS)
	$(EXTENSION_DIR)/pack_safari_extension.sh $(BUILD_DIR) safari $(EXTENSION_DIR)/safari/certs

$(STATIC_SERVER_DIR)/scripts_extraction_test.html: $(STATIC_SERVER_DIR)/html
	$(SCRIPTS_EXTRACTOR) common/static_server/scripts_extraction_test.html $(STATIC_SERVER_DIR)/html/scripts_extraction_test.html

$(CHROME_BUILD_DIR)/helpers.js:
	@cp $(EXTENSION_DIR)/chrome/helpers.js $(CHROME_BUILD_DIR)

$(CHROME_BUILD_DIR)/manifest.json:
	$(ASSIGN_SCRIPTS) chrome chrome/manifest.json $(CHROME_BUILD_DIR)

$(SAFARI_BUILD_DIR)/helpers.js:
	@cp $(EXTENSION_DIR)/safari/helpers.js $(SAFARI_BUILD_DIR)

$(SAFARI_BUILD_DIR)/Info.plist:
	$(ASSIGN_SCRIPTS) safari safari/Info.plist $(SAFARI_BUILD_DIR)

$(FIREFOX_BUILD_DIR)/data/helpers.js: $(FIREFOX_BUILD_DIR)/data
	cat $(EXTENSION_DIR)/firefox/helpers.js firefox/helpers.concat.js > $(FIREFOX_BUILD_DIR)/data/helpers.js

$(FIREFOX_BUILD_DIR)/package.json:
	@cp firefox/package.json $(FIREFOX_BUILD_DIR)

$(FIREFOX_BUILD_DIR)/data/paths.json:
	@cp common/config/paths.json $(FIREFOX_BUILD_DIR)/data

$(SAFARI_BUILD_DIR)/mitro_logo_safari.png:
	@cp $(EXTENSION_DIR)/safari/img/mitro_logo_safari.png $(SAFARI_BUILD_DIR)

$(FIREFOX_BUILD_DIR)/lib/main.js: $(FIREFOX_BUILD_DIR)/lib
	cat $(EXTENSION_DIR)/firefox/main.js firefox/main.concat.js > $(FIREFOX_BUILD_DIR)/lib/main.js

$(FIREFOX_BUILD_DIR)/lib/client.js: $(FIREFOX_BUILD_DIR)/lib
	@cp $(EXTENSION_DIR)/frontend/static/js/client.js $(FIREFOX_BUILD_DIR)/lib

$(FIREFOX_BUILD_DIR)/lib/utils.js: $(FIREFOX_BUILD_DIR)/lib
	@cp $(EXTENSION_DIR)/common/utils.js $(FIREFOX_BUILD_DIR)/lib

