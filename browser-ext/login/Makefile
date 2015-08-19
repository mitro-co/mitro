#
# Makefile to build the extensions onto the build directory
#
#
CLOSURE=./ccache.py java -jar ../third_party/closure-compiler/compiler.jar
SCRIPTS_EXTRACTOR=./extract_scripts.py
MUSTACHE_RENDERER=./mustache_renderer.js
MUSTACHE_COMPILER=$(BUILD_DIR)/../../third_party/hogan.js/bin/hulk
CFX=$(BUILD_DIR)/../../third_party/firefox-addon-sdk/bin/cfx

CLOSURE_STRICT_FLAGS=--language_in ECMASCRIPT5_STRICT --warning_level VERBOSE --compilation_level ADVANCED_OPTIMIZATIONS --externs externs_node.js --jscomp_error missingProperties --jscomp_error checkTypes --jscomp_error checkRegExp --jscomp_error accessControls --jscomp_error const --jscomp_error missingReturn

ifeq ($(shell uname),Darwin)
  GOOGLE_CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
else
  GOOGLE_CHROME=google-chrome
endif

BUILD_DIR:=$(shell pwd)/build
# required for hogan to find its dependencies
export NODE_PATH=$(BUILD_DIR)/../../api/build/node/lib/node_modules
STATIC_DIR:=$(shell pwd)/frontend/static
GEN_DIR:=$(shell pwd)/frontend/gen-files
COMPILED_JS_DIR:=$(BUILD_DIR)/closure
TEMPLATES_DIR=frontend/templates
BASE_TEMPLATES_DIR=frontend/base_templates
PARTIALS_DIR=frontend/partials
HTML_DIR=$(GEN_DIR)/html
JS_DIR=$(GEN_DIR)/js
SUBDIRS=css fonts html img js

# Assigns background and content scripts
# in a browser-specific way to safari/chrome extension
# using common jsons declaration
ASSIGN_SCRIPTS=./assign_scripts.py

CHROME_BUILD_DIR=$(BUILD_DIR)/chrome
CHROME_RELEASE_DIR=$(CHROME_BUILD_DIR)/release
CHROME_RELEASE_SUBDIRS=$(foreach sub,$(SUBDIRS),$(CHROME_RELEASE_DIR)/$(sub))
CHROME_DEBUG_DIR=$(CHROME_BUILD_DIR)/debug
CHROME_DEBUG_SUBDIRS=$(foreach sub,$(SUBDIRS),$(CHROME_DEBUG_DIR)/$(sub))
CHROME_TEST_DIR=$(CHROME_BUILD_DIR)/test

SAFARI_BUILD_DIR=$(BUILD_DIR)/safari
SAFARI_RELEASE_DIR=$(SAFARI_BUILD_DIR)/release.safariextension
SAFARI_RELEASE_SUBDIRS=$(foreach sub,$(SUBDIRS),$(SAFARI_RELEASE_DIR)/$(sub))
SAFARI_DEBUG_DIR=$(SAFARI_BUILD_DIR)/debug.safariextension
SAFARI_DEBUG_SUBDIRS=$(foreach sub,$(SUBDIRS),$(SAFARI_DEBUG_DIR)/$(sub))
SAFARI_TEST_DIR=$(SAFARI_BUILD_DIR)/test.safariextension
SAFARI_TEST_SUBDIRS=$(foreach sub,$(SUBDIRS),$(SAFARI_TEST_DIR)/$(sub))

FIREFOX_BUILD_DIR=$(BUILD_DIR)/firefox
FIREFOX_RELEASE_DIR=$(FIREFOX_BUILD_DIR)/release
FIREFOX_RELEASE_SUBDIRS=$(foreach sub,$(SUBDIRS),$(FIREFOX_RELEASE_DIR)/data/$(sub)) $(FIREFOX_RELEASE_DIR)/data $(FIREFOX_RELEASE_DIR)/lib
FIREFOX_DEBUG_DIR=$(FIREFOX_BUILD_DIR)/debug
FIREFOX_DEBUG_SUBDIRS=$(foreach sub,$(SUBDIRS),$(FIREFOX_DEBUG_DIR)/data/$(sub)) $(FIREFOX_DEBUG_DIR)/data $(FIREFOX_DEBUG_DIR)/lib

FIREFOX_UPDATE_LINK="https://www.mitro.co/firefox/downloads/mitro-login-latest.xpi"
FIREFOX_UPDATE_URL="https://www.mitro.co/firefox/update_rdf"

WEBPAGE_BUILD_DIR=$(BUILD_DIR)/webpage
WEBPAGE_RELEASE_DIR=$(WEBPAGE_BUILD_DIR)/release
WEBPAGE_RELEASE_SUBDIRS=$(foreach sub,$(SUBDIRS),$(WEBPAGE_RELEASE_DIR)/$(sub))
WEBPAGE_DEBUG_DIR=$(WEBPAGE_BUILD_DIR)/debug
WEBPAGE_DEBUG_SUBDIRS=$(foreach sub,$(SUBDIRS),$(WEBPAGE_DEBUG_DIR)/$(sub))
WEBPAGE_TEST_DIR=$(WEBPAGE_BUILD_DIR)/test
WEBPAGE_TEST_SUBDIRS=$(foreach sub,$(SUBDIRS),$(WEBPAGE_TEST_DIR)/$(sub))

COMMON_SRCS=common/*.js
CHROME_SRCS=chrome/*.js
SAFARI_SRCS=safari/*.js
FIREFOX_SRCS=firefox/*.js
WEBPAGE_SRCS=webpage/*.js
MITRO_CLIENT_SRCS=client/*.js

KEYCZAR_SRCS_DIR=../api/build/node/lib/node_modules/keyczarjs
KEYCZARJS_SRCS=$(KEYCZAR_SRCS_DIR)/*.js
$(info kzjs_srouces = $(KEYCZARJS_SRCS))

FORGE_SRCS=$(KEYCZAR_SRCS_DIR)/node_modules/node-forge/js/*.js
$(info forge_srouces = $(FORGE_SRCS))

TESTS=%_test.js %_regtest.js %_regtest2.js
HELPERS=%/helpers.js
MINIFIED_SRCS=%/jquery.min.js %/jquery-ui.min.js %/bootstrap.min.js %/json2.min.js %/underscore-min.js %/forge.min.js
CONTENTSCRIPT_FILES=%/content.js %/contentscriptbase.js %/contentlog.js %/infobar_api.js %/infobar_html.js
COMMON_JS_SRCS=$(filter-out $(TESTS),$(wildcard $(COMMON_SRCS) $(MITRO_CLIENT_SRCS) $(KEYCZARJS_SRCS) $(FORGE_SRCS)))
CHROME_JS_SRCS=$(filter-out $(TESTS),$(wildcard $(COMMON_JS_SRCS) $(CHROME_SRCS)))
SAFARI_JS_SRCS=$(filter-out $(wildcard $(TESTS) $(dir $(SAFARI_SRCS))*-test-specific.js) ,$(wildcard $(COMMON_JS_SRCS) $(SAFARI_SRCS)))
FIREFOX_JS_SRCS=$(filter-out $(TESTS),$(wildcard $(COMMON_JS_SRCS) $(FIREFOX_SRCS)))
WEBPAGE_JS_SRCS=$(filter-out $(CONTENTSCRIPT_FILES) $(TESTS),$(wildcard $(COMMON_JS_SRCS) $(WEBPAGE_SRCS)))
CHROME_HELPERS=$(filter $(HELPERS), $(wildcard $(CHROME_SRCS)))
SAFARI_HELPERS=$(filter $(HELPERS),$(wildcard $(SAFARI_SRCS)))
FIREFOX_HELPERS=$(filter $(HELPERS),$(wildcard $(FIREFOX_SRCS)))
WEBPAGE_HELPERS=$(filter $(HELPERS),$(wildcard $(WEBPAGE_SRCS)))

TEMPLATE_FILES=$(wildcard $(TEMPLATES_DIR)/*mustache)
BASE_TEMPLATE_FILES=$(wildcard $(BASE_TEMPLATES_DIR)/*mustache)
GEN_HTML_FILES=$(TEMPLATE_FILES:$(TEMPLATES_DIR)%mustache=$(HTML_DIR)%html)
PARTIAL_FILES=$(wildcard $(PARTIALS_DIR)/*)
GEN_JS_FILES=$(PARTIAL_FILES:$(PARTIALS_DIR)%mustache=$(JS_DIR)%js)
STATIC_HTML_SRCS=$(wildcard $(STATIC_DIR)/html/*)
STATIC_NONJS_SRCS=$(wildcard $(STATIC_DIR)/css/*.css $(STATIC_DIR)/img/* $(STATIC_DIR)/html/* $(STATIC_DIR)/fonts/*)
STATIC_JS_SRCS=$(filter-out $(TESTS),$(wildcard $(STATIC_DIR)/js/*.js))
GEN_SRCS=$(GEN_HTML_FILES) $(GEN_JS_FILES)

RELEASE_CONFIG=common/config/config.release.js
DEBUG_CONFIG=common/config/config.debug.js
TEST_CONFIG=common/config/config.test.js

CHROME_IMG_SRCS=$(wildcard chrome/img/*)
CHROME_MANIFEST_SRC=chrome/manifest.json
CHROME_CONFIG=chrome/config/config.js

# TODO: write something like dev build rules
SAFARI_IMG_SRCS=$(wildcard safari/img/*)
SAFARI_ICON_SRCS=$(wildcard safari/icon/*)
SAFARI_MANIFEST_SRC=safari/Info.plist
SAFARI_CONFIG=safari/config/config.js
SAFARI_TEST_CONFIG=safari/config/config-test-specific.js

FIREFOX_IMG_SRCS=$(wildcard firefox/img/*)
FIREFOX_MANIFEST_SRC=firefox/package.json
FIREFOX_MAIN_SCRIPT_PATHS=firefox/paths.js
FIREFOX_MAIN_SCRIPT=firefox/main.js
FIREFOX_CONFIG=firefox/config/config.js

WEBPAGE_IMG_SRCS=$(wildcard webpage/img/*)
WEBPAGE_ICON_SRCS=$(wildcard webpage/icon/*)
WEBPAGE_CONFIG=webpage/config/config.js

TARGET_BASENAME=mitro-login-manager

CHROME_RELEASE_DEPS= \
  $(foreach file,$(CHROME_JS_SRCS),$(CHROME_RELEASE_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(CHROME_RELEASE_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(CHROME_RELEASE_DIR),$(file))) \
  $(foreach file,$(CHROME_IMG_SRCS),$(CHROME_RELEASE_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(CHROME_RELEASE_DIR),$(file))) \
  $(CHROME_RELEASE_DIR)/js/helpers.js \
  $(CHROME_RELEASE_DIR)/manifest.json \
  $(CHROME_RELEASE_DIR)/config.js \
  $(CHROME_RELEASE_DIR)/js/config.js \
  $(CHROME_RELEASE_DIR)/infobar_html.js \
  $(CHROME_RELEASE_DIR)/css/mitro_popup2.css \
  $(CHROME_RELEASE_DIR)/css/site.css
CHROME_DEBUG_DEPS= \
  $(foreach file,$(CHROME_JS_SRCS),$(CHROME_DEBUG_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(CHROME_DEBUG_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(CHROME_DEBUG_DIR),$(file))) \
  $(foreach file,$(CHROME_IMG_SRCS),$(CHROME_DEBUG_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(CHROME_DEBUG_DIR),$(file))) \
  $(CHROME_DEBUG_DIR)/js/helpers.js \
  $(CHROME_DEBUG_DIR)/manifest.json \
  $(CHROME_DEBUG_DIR)/config.js \
  $(CHROME_DEBUG_DIR)/js/config.js \
  $(CHROME_DEBUG_DIR)/infobar_html.js \
  $(CHROME_DEBUG_DIR)/css/mitro_popup2.css \
  $(CHROME_DEBUG_DIR)/css/site.css

SAFARI_RELEASE_DEPS= \
  $(foreach file,$(SAFARI_JS_SRCS),$(SAFARI_RELEASE_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(SAFARI_RELEASE_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(SAFARI_RELEASE_DIR),$(file))) \
  $(foreach file,$(SAFARI_IMG_SRCS),$(SAFARI_RELEASE_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(SAFARI_ICON_SRCS),$(SAFARI_RELEASE_DIR)/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(SAFARI_RELEASE_DIR),$(file))) \
  $(SAFARI_RELEASE_DIR)/js/helpers.js \
  $(SAFARI_RELEASE_DIR)/Info.plist \
  $(SAFARI_RELEASE_DIR)/config.js \
  $(SAFARI_RELEASE_DIR)/js/config.js \
  $(SAFARI_RELEASE_DIR)/infobar_html.js \
  $(SAFARI_RELEASE_DIR)/css/mitro_popup2.css \
  $(SAFARI_RELEASE_DIR)/css/site.css
SAFARI_DEBUG_DEPS= \
  $(foreach file,$(SAFARI_JS_SRCS),$(SAFARI_DEBUG_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(SAFARI_DEBUG_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(SAFARI_DEBUG_DIR),$(file))) \
  $(foreach file,$(SAFARI_IMG_SRCS),$(SAFARI_DEBUG_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(SAFARI_ICON_SRCS),$(SAFARI_DEBUG_DIR)/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(SAFARI_DEBUG_DIR),$(file))) \
  $(SAFARI_DEBUG_DIR)/js/helpers.js \
  $(SAFARI_DEBUG_DIR)/Info.plist \
  $(SAFARI_DEBUG_DIR)/config.js \
  $(SAFARI_DEBUG_DIR)/js/config.js \
  $(SAFARI_DEBUG_DIR)/infobar_html.js \
  $(SAFARI_DEBUG_DIR)/css/mitro_popup2.css \
  $(SAFARI_DEBUG_DIR)/css/site.css
SAFARI_TEST_DEPS= \
  $(foreach file,$(SAFARI_JS_SRCS),$(SAFARI_TEST_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(SAFARI_TEST_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(SAFARI_TEST_DIR),$(file))) \
  $(foreach file,$(SAFARI_IMG_SRCS),$(SAFARI_TEST_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(SAFARI_ICON_SRCS),$(SAFARI_TEST_DIR)/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(SAFARI_TEST_DIR),$(file))) \
  $(SAFARI_TEST_DIR)/js/helpers.js \
  $(SAFARI_TEST_DIR)/Info.plist \
  $(SAFARI_TEST_DIR)/config.js \
  $(SAFARI_TEST_DIR)/js/config.js \
  $(SAFARI_TEST_DIR)/infobar_html.js \
  $(SAFARI_TEST_DIR)/css/mitro_popup2.css \
  $(SAFARI_TEST_DIR)/css/site.css

FIREFOX_RELEASE_DEPS= \
  $(foreach file,$(FIREFOX_JS_SRCS),$(FIREFOX_RELEASE_DIR)/data/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(FIREFOX_RELEASE_DIR)/data/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(FIREFOX_RELEASE_DIR)/data,$(file))) \
  $(foreach file,$(FIREFOX_IMG_SRCS),$(FIREFOX_RELEASE_DIR)/data/img/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(FIREFOX_RELEASE_DIR)/data,$(file))) \
  $(FIREFOX_RELEASE_DIR)/data/js/helpers.js \
  $(FIREFOX_RELEASE_DIR)/data/config.js \
  $(FIREFOX_RELEASE_DIR)/data/js/config.js \
  $(FIREFOX_RELEASE_DIR)/lib/main.js \
  $(FIREFOX_RELEASE_DIR)/package.json \
  $(FIREFOX_RELEASE_DIR)/data/paths.json \
  $(FIREFOX_RELEASE_DIR)/data/infobar_html.js \
  $(FIREFOX_RELEASE_DIR)/data/background.html \
  $(FIREFOX_RELEASE_DIR)/button.html \
  $(FIREFOX_RELEASE_DIR)/data/css/mitro_popup2.css \
  $(FIREFOX_RELEASE_DIR)/data/css/site.css
FIREFOX_DEBUG_DEPS= \
  $(foreach file,$(FIREFOX_JS_SRCS),$(FIREFOX_DEBUG_DIR)/data/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(FIREFOX_DEBUG_DIR)/data/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(FIREFOX_DEBUG_DIR)/data,$(file))) \
  $(foreach file,$(FIREFOX_IMG_SRCS),$(FIREFOX_DEBUG_DIR)/data/img/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(FIREFOX_DEBUG_DIR)/data,$(file))) \
  $(FIREFOX_DEBUG_DIR)/data/js/helpers.js \
  $(FIREFOX_DEBUG_DIR)/data/config.js \
  $(FIREFOX_DEBUG_DIR)/data/js/config.js \
  $(FIREFOX_DEBUG_DIR)/lib/main.js \
  $(FIREFOX_DEBUG_DIR)/package.json \
  $(FIREFOX_DEBUG_DIR)/data/paths.json \
  $(FIREFOX_DEBUG_DIR)/data/infobar_html.js \
  $(FIREFOX_DEBUG_DIR)/data/background.html \
  $(FIREFOX_DEBUG_DIR)/button.html \
  $(FIREFOX_DEBUG_DIR)/data/css/mitro_popup2.css \
  $(FIREFOX_DEBUG_DIR)/data/css/site.css

WEBPAGE_RELEASE_DEPS= \
  $(foreach file,$(WEBPAGE_JS_SRCS),$(WEBPAGE_RELEASE_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(WEBPAGE_RELEASE_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(WEBPAGE_RELEASE_DIR),$(file))) \
  $(foreach file,$(WEBPAGE_IMG_SRCS),$(WEBPAGE_RELEASE_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(WEBPAGE_ICON_SRCS),$(WEBPAGE_RELEASE_DIR)/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(WEBPAGE_RELEASE_DIR),$(file))) \
  $(WEBPAGE_RELEASE_DIR)/js/helpers.js \
  $(WEBPAGE_RELEASE_DIR)/config.js \
  $(WEBPAGE_RELEASE_DIR)/js/config.js \
  $(WEBPAGE_RELEASE_DIR)/css/mitro_popup2.css \
  $(WEBPAGE_RELEASE_DIR)/css/site.css \
  $(WEBPAGE_RELEASE_DIR)/index.html \
  $(WEBPAGE_RELEASE_DIR)/background.html
WEBPAGE_DEBUG_DEPS= \
  $(foreach file,$(WEBPAGE_JS_SRCS),$(WEBPAGE_DEBUG_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(WEBPAGE_DEBUG_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(WEBPAGE_DEBUG_DIR),$(file))) \
  $(foreach file,$(WEBPAGE_IMG_SRCS),$(WEBPAGE_DEBUG_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(WEBPAGE_ICON_SRCS),$(WEBPAGE_DEBUG_DIR)/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(WEBPAGE_DEBUG_DIR),$(file))) \
  $(WEBPAGE_DEBUG_DIR)/js/helpers.js \
  $(WEBPAGE_DEBUG_DIR)/config.js \
  $(WEBPAGE_DEBUG_DIR)/js/config.js \
  $(WEBPAGE_DEBUG_DIR)/css/mitro_popup2.css \
  $(WEBPAGE_DEBUG_DIR)/css/site.css \
  $(WEBPAGE_DEBUG_DIR)/index.html \
  $(WEBPAGE_DEBUG_DIR)/background.html
WEBPAGE_TEST_DEPS= \
  $(foreach file,$(WEBPAGE_JS_SRCS),$(WEBPAGE_TEST_DIR)/$(notdir $(file))) \
  $(foreach file,$(STATIC_JS_SRCS),$(WEBPAGE_TEST_DIR)/js/$(notdir $(file))) \
  $(foreach file,$(STATIC_NONJS_SRCS),$(subst $(STATIC_DIR),$(WEBPAGE_TEST_DIR),$(file))) \
  $(foreach file,$(WEBPAGE_IMG_SRCS),$(WEBPAGE_TEST_DIR)/img/$(notdir $(file))) \
  $(foreach file,$(WEBPAGE_ICON_SRCS),$(WEBPAGE_TEST_DIR)/$(notdir $(file))) \
  $(foreach file,$(GEN_SRCS),$(subst $(GEN_DIR),$(WEBPAGE_TEST_DIR),$(file))) \
  $(WEBPAGE_TEST_DIR)/js/helpers.js \
  $(WEBPAGE_TEST_DIR)/config.js \
  $(WEBPAGE_TEST_DIR)/js/config.js \
  $(WEBPAGE_TEST_DIR)/css/mitro_popup2.css \
  $(WEBPAGE_TEST_DIR)/css/site.css \
  $(WEBPAGE_TEST_DIR)/index.html \
  $(WEBPAGE_TEST_DIR)/background.html

CLOSURE_OUTPUTS= \
	$(COMPILED_JS_DIR)/mitroclient_compiled.js \
	$(COMPILED_JS_DIR)/mitroclient_test_compiled.js \
	$(COMPILED_JS_DIR)/password_generator_compiled.js \
	$(COMPILED_JS_DIR)/password_generator_test_compiled.js \
	$(COMPILED_JS_DIR)/userdata_compiled.js \
	$(COMPILED_JS_DIR)/userdata_test_compiled.js \
	$(COMPILED_JS_DIR)/csvutil_test_compiled.js \
	$(COMPILED_JS_DIR)/exportsecrets_test_compiled.js \
	$(COMPILED_JS_DIR)/import-common_test_compiled.js

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
	@$(SCRIPTS_EXTRACTOR) extract $(1) $(2)
endef

define CLOSURE_COMPILER_RULE
$(2): $(1) | $(dir $(2))
	@echo $(2)
	@$(CLOSURE) --js $(1) --js_output_file $(2) || exit $$?
endef

define MUSTACHE_RENDERER_RULE
$(2): $(1) $(dir $(2)) $(BASE_TEMPLATE_FILES)
	@echo $(2)
	@NODE_PATH=../third_party node $(MUSTACHE_RENDERER) $(1) $(abspath $(2))
endef

define MUSTACHE_COMPILER_RULE
$(2): $(1) $(dir $(2))
	@echo $(2)
	@$(MUSTACHE_COMPILER) $(1) > $(abspath $(2))
endef

.PHONY: all release debug clean test closure chrome firefox firefox-debug firefox-release safari safari-debug safari-release all_generated_outputs webpage webpage-debug webpage-release webpage-test
all: chrome closure test
chrome: $(CHROME_BUILD_DIR)/$(TARGET_BASENAME).zip
firefox: $(FIREFOX_RELEASE_DIR)/$(TARGET_BASENAME).xpi
safari: $(SAFARI_RELEASE_DEPS)
closure: $(CLOSURE_OUTPUTS)
webpage: $(WEBPAGE_RELEASE_DEPS)

clean:
	rm -rf $(GEN_DIR)
	rm -rf $(BUILD_DIR)

release: $(CHROME_RELEASE_DEPS)
debug: $(CHROME_DEBUG_DEPS)

firefox-release: $(firefox)
firefox-debug: $(FIREFOX_DEBUG_DIR)/debug.xpi

safari-release: $(safari)
safari-debug: $(SAFARI_DEBUG_DEPS)

webpage-release: $(webpage)
webpage-debug: $(WEBPAGE_DEBUG_DEPS)
webpage-test: $(WEBPAGE_TEST_DEPS)

$(CHROME_BUILD_DIR)/$(TARGET_BASENAME).zip: $(CHROME_RELEASE_DEPS)
	zip -r9 $@ $(CHROME_RELEASE_DIR)/*

# Needed for testing extension with selenium.
# If we use decide to pack the extension for the web store, we'll want to
# save the key.
$(CHROME_BUILD_DIR)/debug.crx: $(CHROME_DEBUG_DEPS)
	$(GOOGLE_CHROME) --pack-extension=$(CHROME_DEBUG_DIR)
	rm -f $(CHROME_BUILD_DIR)/debug.pem

$(CHROME_BUILD_DIR)/test.crx: $(CHROME_DEBUG_DEPS) $(CHROME_CONFIG)
	rm -rf $(CHROME_TEST_DIR)
	cp -r $(CHROME_DEBUG_DIR) $(CHROME_TEST_DIR)
	cat $(TEST_CONFIG) $(CHROME_CONFIG) > $(CHROME_TEST_DIR)/config.js
	cat $(TEST_CONFIG) $(CHROME_CONFIG) > $(CHROME_TEST_DIR)/js/config.js
	$(GOOGLE_CHROME) --pack-extension=$(CHROME_TEST_DIR)
	rm -f $(CHROME_BUILD_DIR)/test.pem

# CRX file for testing with mitro dev server.
test/mitro-test.crx: $(CHROME_BUILD_DIR)/test.crx
	cp $^ $@

# Directories
$(foreach d,$(CHROME_RELEASE_DIR) $(CHROME_RELEASE_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(CHROME_DEBUG_DIR) $(CHROME_DEBUG_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(SAFARI_RELEASE_DIR) $(SAFARI_RELEASE_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(SAFARI_DEBUG_DIR) $(SAFARI_DEBUG_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(SAFARI_TEST_DIR) $(SAFARI_TEST_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(FIREFOX_RELEASE_DIR) $(FIREFOX_RELEASE_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(FIREFOX_DEBUG_DIR) $(FIREFOX_DEBUG_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(WEBPAGE_RELEASE_DIR) $(WEBPAGE_RELEASE_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(WEBPAGE_DEBUG_DIR) $(WEBPAGE_DEBUG_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(foreach d,$(WEBPAGE_TEST_DIR) $(WEBPAGE_TEST_SUBDIRS),$(eval $(call MKDIR_RULE,$(d))))
$(eval $(call MKDIR_RULE,$(HTML_DIR)))
$(eval $(call MKDIR_RULE,$(JS_DIR)))
$(eval $(call MKDIR_RULE,$(COMPILED_JS_DIR)))

# CHROME_JS_SRCS
$(foreach file,$(filter-out $(MINIFIED_SRCS),$(CHROME_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(CHROME_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(CHROME_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(CHROME_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(CHROME_HELPERS),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(CHROME_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(CHROME_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(CHROME_DEBUG_DIR)/$(notdir $(file)))))
$(foreach file,$(CHROME_HELPERS),$(eval $(call COPY_RULE,$(file),$(CHROME_DEBUG_DIR)/js/$(notdir $(file)))))

# SAFARI_JS_SRCS
$(foreach file,$(filter-out $(MINIFIED_SRCS),$(SAFARI_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(SAFARI_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(SAFARI_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(SAFARI_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(SAFARI_HELPERS),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(SAFARI_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(SAFARI_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_DEBUG_DIR)/$(notdir $(file)))))
$(foreach file,$(SAFARI_HELPERS),$(eval $(call COPY_RULE,$(file),$(SAFARI_DEBUG_DIR)/js/$(notdir $(file)))))
$(foreach file,$(filter-out $(wildcard $(dir $(COMMON_SRCS))background.js $(dir $(SAFARI_SRCS))helpers.js), $(wildcard $(SAFARI_JS_SRCS))),$(eval $(call COPY_RULE,$(file),$(SAFARI_TEST_DIR)/$(notdir $(file)))))
$(foreach file,$(filter-ort $(dir $(SAFARI_SRCS))helpers.js, $(SAFARI_HELPERS)),$(eval $(call COPY_RULE,$(file),$(SAFARI_TEST_DIR)/js/$(notdir $(file)))))

$(SAFARI_TEST_DIR)/background.js:
	cat $(dir $(COMMON_SRCS))background.js $(dir $(SAFARI_SRCS))background-test-specific.js > $(SAFARI_TEST_DIR)/background.js

$(SAFARI_TEST_DIR)/helpers.js:
	cat $(dir $(SAFARI_SRCS))helpers.js $(dir $(SAFARI_SRCS))helpers-test-specific.js > $(SAFARI_TEST_DIR)/helpers.js

$(SAFARI_TEST_DIR)/js/helpers.js:
	cat $(dir $(SAFARI_SRCS))helpers.js $(dir $(SAFARI_SRCS))helpers-test-specific.js > $(SAFARI_TEST_DIR)/js/helpers.js

# FIREFOX_JS_SRCS
$(foreach file,$(filter-out $(MINIFIED_SRCS),$(FIREFOX_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(FIREFOX_RELEASE_DIR)/data/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(FIREFOX_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(FIREFOX_RELEASE_DIR)/data/$(notdir $(file)))))
$(foreach file,$(FIREFOX_HELPERS),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(FIREFOX_RELEASE_DIR)/data/js/$(notdir $(file)))))
$(foreach file,$(FIREFOX_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(FIREFOX_DEBUG_DIR)/data/$(notdir $(file)))))
$(foreach file,$(FIREFOX_HELPERS),$(eval $(call COPY_RULE,$(file),$(FIREFOX_DEBUG_DIR)/data/js/$(notdir $(file)))))

# WEBPAGE_JS_SRCS
$(foreach file,$(filter-out $(MINIFIED_SRCS),$(WEBPAGE_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(WEBPAGE_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(WEBPAGE_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_HELPERS),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(WEBPAGE_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_DEBUG_DIR)/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_HELPERS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_DEBUG_DIR)/js/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_TEST_DIR)/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_HELPERS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_TEST_DIR)/js/$(notdir $(file)))))

# STATIC_JS_SRCS
$(foreach file,$(filter-out $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(CHROME_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(CHROME_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(STATIC_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(CHROME_DEBUG_DIR)/js/$(notdir $(file)))))

$(foreach file,$(filter-out $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(SAFARI_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(SAFARI_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(STATIC_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_DEBUG_DIR)/js/$(notdir $(file)))))
$(foreach file,$(STATIC_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_TEST_DIR)/js/$(notdir $(file)))))

$(foreach file,$(filter-out $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(FIREFOX_RELEASE_DIR)/data/js/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(FIREFOX_RELEASE_DIR)/data/js/$(notdir $(file)))))
$(foreach file,$(STATIC_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(FIREFOX_DEBUG_DIR)/data/js/$(notdir $(file)))))

$(foreach file,$(filter-out $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call CLOSURE_COMPILER_RULE,$(file),$(WEBPAGE_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(filter $(MINIFIED_SRCS),$(STATIC_JS_SRCS)),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_RELEASE_DIR)/js/$(notdir $(file)))))
$(foreach file,$(STATIC_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_DEBUG_DIR)/js/$(notdir $(file)))))
$(foreach file,$(STATIC_JS_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_TEST_DIR)/js/$(notdir $(file)))))

# GEN_SRCS
$(foreach file,$(GEN_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(CHROME_RELEASE_DIR),$(file)))))
$(foreach file,$(GEN_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(CHROME_DEBUG_DIR),$(file)))))

$(foreach file,$(GEN_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(SAFARI_RELEASE_DIR),$(file)))))
$(foreach file,$(GEN_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(SAFARI_DEBUG_DIR),$(file)))))
$(foreach file,$(filter-out $(GEN_HTML_FILES),$(GEN_SRCS)),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(SAFARI_TEST_DIR),$(file)))))
$(foreach file,$(filter $(GEN_HTML_FILES),$(GEN_SRCS)),$(eval $(call EXTRACT_SCRIPTS_RULE,$(file),$(subst $(GEN_DIR),$(SAFARI_TEST_DIR),$(file)))))

$(foreach file,$(filter-out $(GEN_HTML_FILES),$(GEN_SRCS)),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(FIREFOX_RELEASE_DIR)/data,$(file)))))
$(foreach file,$(filter $(GEN_HTML_FILES),$(GEN_SRCS)),$(eval $(call EXTRACT_SCRIPTS_RULE,$(file),$(subst $(GEN_DIR),$(FIREFOX_RELEASE_DIR)/data,$(file)))))
$(foreach file,$(filter-out $(GEN_HTML_FILES),$(GEN_SRCS)),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(FIREFOX_DEBUG_DIR)/data,$(file)))))
$(foreach file,$(filter $(GEN_HTML_FILES),$(GEN_SRCS)),$(eval $(call EXTRACT_SCRIPTS_RULE,$(file),$(subst $(GEN_DIR),$(FIREFOX_DEBUG_DIR)/data,$(file)))))

$(foreach file,$(GEN_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(WEBPAGE_RELEASE_DIR),$(file)))))
$(foreach file,$(GEN_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(WEBPAGE_DEBUG_DIR),$(file)))))
$(foreach file,$(GEN_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(GEN_DIR),$(WEBPAGE_TEST_DIR),$(file)))))

# GEN_JS_FILES
$(foreach file,$(GEN_JS_FILES),$(eval $(call MUSTACHE_COMPILER_RULE,$(PARTIALS_DIR)/$(notdir $(basename $(file))).mustache,$(file))))

# GEN_HTML_FILES
$(foreach file,$(GEN_HTML_FILES),$(eval $(call MUSTACHE_RENDERER_RULE,$(patsubst $(HTML_DIR)%.html,$(TEMPLATES_DIR)%.mustache,$(file)),$(file))))

# STATIC_NONJS_SRCS
$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(CHROME_RELEASE_DIR),$(file)))))
$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(CHROME_DEBUG_DIR),$(file)))))

$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(SAFARI_RELEASE_DIR),$(file)))))
$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(SAFARI_DEBUG_DIR),$(file)))))
$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(SAFARI_TEST_DIR),$(file)))))

$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(FIREFOX_RELEASE_DIR)/data,$(file)))))
$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(FIREFOX_DEBUG_DIR)/data,$(file)))))

$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(WEBPAGE_RELEASE_DIR),$(file)))))
$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(WEBPAGE_DEBUG_DIR),$(file)))))
$(foreach file,$(STATIC_NONJS_SRCS),$(eval $(call COPY_RULE,$(file),$(subst $(STATIC_DIR),$(WEBPAGE_TEST_DIR),$(file)))))

# IMG_SRCS
$(foreach file,$(CHROME_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(CHROME_RELEASE_DIR)/img/$(notdir $(file)))))
$(foreach file,$(CHROME_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(CHROME_DEBUG_DIR)/img/$(notdir $(file)))))
$(foreach file,$(SAFARI_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_RELEASE_DIR)/img/$(notdir $(file)))))
$(foreach file,$(SAFARI_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_DEBUG_DIR)/img/$(notdir $(file)))))
$(foreach file,$(SAFARI_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_TEST_DIR)/img/$(notdir $(file)))))
$(foreach file,$(FIREFOX_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(FIREFOX_RELEASE_DIR)/data/img/$(notdir $(file)))))
$(foreach file,$(FIREFOX_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(FIREFOX_DEBUG_DIR)/data/img/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_RELEASE_DIR)/img/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_DEBUG_DIR)/img/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_IMG_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_TEST_DIR)/img/$(notdir $(file)))))

# SAFARI ICONS
$(foreach file,$(SAFARI_ICON_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(SAFARI_ICON_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_DEBUG_DIR)/$(notdir $(file)))))
$(foreach file,$(SAFARI_ICON_SRCS),$(eval $(call COPY_RULE,$(file),$(SAFARI_TEST_DIR)/$(notdir $(file)))))

# WEBPAGE ICONS
$(foreach file,$(WEBPAGE_ICON_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_RELEASE_DIR)/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_ICON_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_DEBUG_DIR)/$(notdir $(file)))))
$(foreach file,$(WEBPAGE_ICON_SRCS),$(eval $(call COPY_RULE,$(file),$(WEBPAGE_TEST_DIR)/$(notdir $(file)))))

# CHROME_MANIFEST
$(CHROME_RELEASE_DIR)/manifest.json: $(CHROME_MANIFEST_SRC)
	$(ASSIGN_SCRIPTS) chrome chrome/manifest.json $(CHROME_RELEASE_DIR)
$(CHROME_DEBUG_DIR)/manifest.json: $(CHROME_MANIFEST_SRC)
	$(ASSIGN_SCRIPTS) chrome chrome/manifest.json $(CHROME_DEBUG_DIR)

# SAFARI INFO.PLIST
$(SAFARI_RELEASE_DIR)/Info.plist: $(SAFARI_MANIFEST_SRC)
	$(ASSIGN_SCRIPTS) safari safari/Info.plist $(SAFARI_RELEASE_DIR)
$(SAFARI_DEBUG_DIR)/Info.plist: $(SAFARI_MANIFEST_SRC)
	$(ASSIGN_SCRIPTS) safari safari/Info.plist $(SAFARI_DEBUG_DIR)
$(SAFARI_TEST_DIR)/Info.plist: $(SAFARI_MANIFEST_SRC)
	$(ASSIGN_SCRIPTS) safari safari/Info.plist $(SAFARI_TEST_DIR)

# FIREFOX PACKAGE.JSON
$(FIREFOX_RELEASE_DIR)/package.json: $(FIREFOX_MANIFEST_SRC)
	grep -v -h '^ *[/][/]' $^ >$@
$(FIREFOX_DEBUG_DIR)/package.json: $(FIREFOX_MANIFEST_SRC)
	grep -v -h '^ *[/][/]' $^ >$@


# FIREFOX BACKGROUND PAGE
$(FIREFOX_RELEASE_DIR)/data/background.html:
	cp $(dir $(FIREFOX_SRCS))background.html $(FIREFOX_RELEASE_DIR)/data/
$(FIREFOX_DEBUG_DIR)/data/background.html:
	cp $(dir $(FIREFOX_SRCS))background.html $(FIREFOX_DEBUG_DIR)/data/
$(FIREFOX_RELEASE_DIR)/button.html:
	cp $(dir $(FIREFOX_SRCS))button.html $(FIREFOX_RELEASE_DIR)/data/
$(FIREFOX_DEBUG_DIR)/button.html:
	cp $(dir $(FIREFOX_SRCS))button.html $(FIREFOX_DEBUG_DIR)/data/

# WEBPAGE BACKGROUND AND INDEX HTMLS
$(WEBPAGE_RELEASE_DIR)/index.html:
	cp $(dir $(WEBPAGE_SRCS))index.html $(WEBPAGE_RELEASE_DIR)
$(WEBPAGE_RELEASE_DIR)/background.html:
	cp $(dir $(WEBPAGE_SRCS))background.html $(WEBPAGE_RELEASE_DIR)
$(WEBPAGE_DEBUG_DIR)/index.html:
	cp $(dir $(WEBPAGE_SRCS))index.html $(WEBPAGE_DEBUG_DIR)
$(WEBPAGE_DEBUG_DIR)/background.html:
	cp $(dir $(WEBPAGE_SRCS))background.html $(WEBPAGE_DEBUG_DIR)
$(WEBPAGE_TEST_DIR)/index.html:
	cp $(dir $(WEBPAGE_SRCS))index.html $(WEBPAGE_TEST_DIR)
$(WEBPAGE_TEST_DIR)/background.html:
	cp $(dir $(WEBPAGE_SRCS))background.html $(WEBPAGE_TEST_DIR)

# Config files
$(CHROME_RELEASE_DIR)/config.js: $(RELEASE_CONFIG) | $(CHROME_RELEASE_DIR)
	cat $(RELEASE_CONFIG) $(CHROME_CONFIG) > $(CHROME_RELEASE_DIR)/config.js
$(CHROME_RELEASE_DIR)/js/config.js: $(RELEASE_CONFIG) | $(CHROME_RELEASE_DIR)/js
	cat $(RELEASE_CONFIG) $(CHROME_CONFIG) > $(CHROME_RELEASE_DIR)/js/config.js
$(CHROME_DEBUG_DIR)/config.js: $(DEBUG_CONFIG) | $(CHROME_DEBUG_DIR)
	cat $(DEBUG_CONFIG) $(CHROME_CONFIG) > $(CHROME_DEBUG_DIR)/config.js
$(CHROME_DEBUG_DIR)/js/config.js: $(DEBUG_CONFIG) | $(CHROME_DEBUG_DIR)/js
	cat $(DEBUG_CONFIG) $(CHROME_CONFIG) > $(CHROME_DEBUG_DIR)/js/config.js
$(SAFARI_RELEASE_DIR)/config.js: $(RELEASE_CONFIG) | $(SAFARI_RELEASE_DIR)
	cat $(RELEASE_CONFIG) $(SAFARI_CONFIG) > $(SAFARI_RELEASE_DIR)/config.js
$(SAFARI_RELEASE_DIR)/js/config.js: $(RELEASE_CONFIG) | $(SAFARI_RELEASE_DIR)/js
	cat $(RELEASE_CONFIG) $(SAFARI_CONFIG) > $(SAFARI_RELEASE_DIR)/js/config.js
$(SAFARI_DEBUG_DIR)/config.js: $(DEBUG_CONFIG) | $(SAFARI_DEBUG_DIR)
	cat $(DEBUG_CONFIG) $(SAFARI_CONFIG) > $(SAFARI_DEBUG_DIR)/config.js
$(SAFARI_DEBUG_DIR)/js/config.js: $(DEBUG_CONFIG) | $(SAFARI_DEBUG_DIR)/js
	cat $(DEBUG_CONFIG) $(SAFARI_CONFIG) > $(SAFARI_DEBUG_DIR)/js/config.js
$(SAFARI_TEST_DIR)/config.js: $(TEST_CONFIG) | $(SAFARI_TEST_DIR)
	cat $(TEST_CONFIG) $(SAFARI_CONFIG) $(SAFARI_TEST_CONFIG) > $(SAFARI_TEST_DIR)/config.js
$(SAFARI_TEST_DIR)/js/config.js: $(TEST_CONFIG) | $(SAFARI_TEST_DIR)/js
	cat $(TEST_CONFIG) $(SAFARI_CONFIG) $(SAFARI_TEST_CONFIG) > $(SAFARI_TEST_DIR)/js/config.js
$(FIREFOX_RELEASE_DIR)/data/config.js: $(RELEASE_CONFIG) | $(FIREFOX_RELEASE_DIR)
	cat $(RELEASE_CONFIG) $(FIREFOX_CONFIG) > $(FIREFOX_RELEASE_DIR)/data/config.js
$(FIREFOX_RELEASE_DIR)/data/js/config.js: $(RELEASE_CONFIG) | $(FIREFOX_RELEASE_DIR)
	cat $(RELEASE_CONFIG) $(FIREFOX_CONFIG) > $(FIREFOX_RELEASE_DIR)/data/js/config.js
$(FIREFOX_DEBUG_DIR)/data/config.js: $(DEBUG_CONFIG) | $(FIREFOX_DEBUG_DIR)
	cat $(DEBUG_CONFIG) $(FIREFOX_CONFIG) > $(FIREFOX_DEBUG_DIR)/data/config.js
$(FIREFOX_DEBUG_DIR)/data/js/config.js: $(DEBUG_CONFIG) | $(FIREFOX_DEBUG_DIR)
	cat $(DEBUG_CONFIG) $(FIREFOX_CONFIG) > $(FIREFOX_DEBUG_DIR)/data/js/config.js
$(WEBPAGE_RELEASE_DIR)/config.js: $(RELEASE_CONFIG) | $(WEBPAGE_RELEASE_DIR)
	cat $(RELEASE_CONFIG) $(WEBPAGE_CONFIG) > $(WEBPAGE_RELEASE_DIR)/config.js
$(WEBPAGE_RELEASE_DIR)/js/config.js: $(RELEASE_CONFIG) | $(WEBPAGE_RELEASE_DIR)/js
	cat $(RELEASE_CONFIG) $(WEBPAGE_CONFIG) > $(WEBPAGE_RELEASE_DIR)/js/config.js
$(WEBPAGE_DEBUG_DIR)/config.js: $(DEBUG_CONFIG) | $(WEBPAGE_DEBUG_DIR)
	cat $(DEBUG_CONFIG) $(WEBPAGE_CONFIG) > $(WEBPAGE_DEBUG_DIR)/config.js
$(WEBPAGE_DEBUG_DIR)/js/config.js: $(DEBUG_CONFIG) | $(WEBPAGE_DEBUG_DIR)/js
	cat $(DEBUG_CONFIG) $(WEBPAGE_CONFIG) > $(WEBPAGE_DEBUG_DIR)/js/config.js
$(WEBPAGE_TEST_DIR)/config.js: $(TEST_CONFIG) | $(WEBPAGE_TEST_DIR)
	cat $(TEST_CONFIG) $(WEBPAGE_CONFIG) $(WEBPAGE_TEST_CONFIG) > $(WEBPAGE_TEST_DIR)/config.js
$(WEBPAGE_TEST_DIR)/js/config.js: $(TEST_CONFIG) | $(WEBPAGE_TEST_DIR)/js
	cat $(TEST_CONFIG) $(WEBPAGE_CONFIG) $(WEBPAGE_TEST_CONFIG) > $(WEBPAGE_TEST_DIR)/js/config.js

# Firefox main script
$(FIREFOX_RELEASE_DIR)/data/package.json: $(FIREFOX_MAIN_SCRIPT_PATHS) | $(FIREFOX_RELEASE_DIR)/data
	cp $< $@
$(FIREFOX_DEBUG_DIR)/data/package.json: $(FIREFOX_MAIN_SCRIPT_PATHS) | $(FIREFOX_DEBUG_DIR)/data
	cp $< $@
$(FIREFOX_RELEASE_DIR)/lib/main.js: $(FIREFOX_MAIN_SCRIPT) | $(FIREFOX_RELEASE_DIR)/lib
	cp $< $@
$(FIREFOX_DEBUG_DIR)/lib/main.js: $(FIREFOX_MAIN_SCRIPT) | $(FIREFOX_DEBUG_DIR)/lib
	cp $< $@
$(FIREFOX_RELEASE_DIR)/data/paths.json: common/config/paths.json | $(FIREFOX_RELEASE_DIR)/data
	cp $< $@
$(FIREFOX_DEBUG_DIR)/data/paths.json: common/config/paths.json | $(FIREFOX_DEBUG_DIR)/data
	cp $< $@

# infobar_html.js
# Due to iframe origins, we inject the HTML as Javascript
%/infobar_html.js: $* common/infobar.html makejsresource.py
	$(RM) $*/infobar_html.js
	./makejsresource.py common/infobar.html $*/infobar_html.js

# TODO(ivan): make html cleanup less ugly, make the utils.js copy in a 'make' ways
$(FIREFOX_RELEASE_DIR)/$(TARGET_BASENAME).xpi: $(FIREFOX_RELEASE_DEPS)
	# getting the script tags commented
	cp $(FIREFOX_RELEASE_DIR)/data/js/client.js $(FIREFOX_RELEASE_DIR)/lib
	cp $(FIREFOX_RELEASE_DIR)/data/utils.js $(FIREFOX_RELEASE_DIR)/lib
	cp $(FIREFOX_RELEASE_DIR)/data/helpers_common.js $(FIREFOX_RELEASE_DIR)/lib

	$(SCRIPTS_EXTRACTOR) collect $(FIREFOX_RELEASE_DIR)/data

	# building xpi
	cd $(FIREFOX_BUILD_DIR) && \
	$(CFX) xpi --pkgdir=release --update-link=$(FIREFOX_UPDATE_LINK) --update-url=$(FIREFOX_UPDATE_URL) && \
	mv $(TARGET_BASENAME).xpi release.xpi && \
	mv $(TARGET_BASENAME).update.rdf update_rdf

$(FIREFOX_DEBUG_DIR)/debug.xpi: $(FIREFOX_DEBUG_DEPS)
	# getting the script tags commented
	cp $(FIREFOX_DEBUG_DIR)/data/js/client.js $(FIREFOX_DEBUG_DIR)/lib
	cp $(FIREFOX_DEBUG_DIR)/data/utils.js $(FIREFOX_DEBUG_DIR)/lib
	cp $(FIREFOX_DEBUG_DIR)/data/helpers_common.js $(FIREFOX_DEBUG_DIR)/lib

	$(SCRIPTS_EXTRACTOR) collect $(FIREFOX_DEBUG_DIR)/data

	# building xpi
	cd $(FIREFOX_BUILD_DIR) && \
	$(CFX) xpi --pkgdir=debug && \
	mv $(TARGET_BASENAME).xpi debug.xpi

test: test/mitro-test.crx
	cd common && ./runtests.sh
	cd test && ./runtests.sh

safari-test: $(SAFARI_TEST_DEPS)
	$(SCRIPTS_EXTRACTOR) collect $(SAFARI_TEST_DIR)

$(COMPILED_JS_DIR)/mitroclient_compiled.js: ../api/js/cli/kew.js ../api/js/cli/mitro_legacyapi.js ../api/js/cli/mitroclient.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --js_output_file $@ $^
$(COMPILED_JS_DIR)/mitroclient_test_compiled.js: ../api/js/cli/kew.js ../api/js/cli/mitro_legacyapi.js ../api/js/cli/mitroclient.js ../api/js/cli/mitroclient_test.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --externs ../api/js/cli/externs_jasmine.js --js_output_file $@ $^
$(COMPILED_JS_DIR)/password_generator_compiled.js: ../api/js/cli/password_generator.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --js_output_file $@ $^
$(COMPILED_JS_DIR)/password_generator_test_compiled.js: ../api/js/cli/password_generator.js ../api/js/cli/password_generator_test.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --externs ../api/js/cli/externs_jasmine.js --js_output_file $@ $^
$(COMPILED_JS_DIR)/userdata_compiled.js: frontend/static/js/background_interface.js ../api/js/cli/kew.js common/utils.js frontend/static/js/org-info.js frontend/static/js/userdata.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --js_output_file $@ $^
$(COMPILED_JS_DIR)/userdata_test_compiled.js: frontend/static/js/background_interface.js ../api/js/cli/kew.js common/utils.js frontend/static/js/org-info.js frontend/static/js/userdata.js frontend/static/js/mocks.js frontend/static/js/userdata_test.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --externs ../api/js/cli/externs_jasmine.js --js_output_file $@ $^
$(COMPILED_JS_DIR)/csvutil_test_compiled.js: frontend/static/js/csvutil.js frontend/static/js/csvutil_test.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --externs ../api/js/cli/externs_jasmine.js --js_output_file $@ $^
$(COMPILED_JS_DIR)/exportsecrets_test_compiled.js: ../api/js/cli/kew.js frontend/static/js/background_interface.js frontend/static/js/exportsecrets.js frontend/static/js/mocks.js frontend/static/js/exportsecrets_test.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --externs ../api/js/cli/externs_jasmine.js --js_output_file $@ $^
$(COMPILED_JS_DIR)/import-common_test_compiled.js: ../api/js/cli/kew.js frontend/static/js/background_interface.js frontend/static/js/exportsecrets.js frontend/static/js/mocks.js frontend/static/js/admin-common.js common/utils.js frontend/static/js/org-info.js frontend/static/js/userdata.js frontend/static/js/import-common.js frontend/static/js/import-common_test.js | $(COMPILED_JS_DIR)
	$(CLOSURE) $(CLOSURE_STRICT_FLAGS) --externs ../api/js/cli/externs_bootstrap.js --externs ../api/js/cli/externs_underscore.js --externs ../api/js/cli/externs_jasmine.js --externs ../api/js/cli/externs_jquery.js --externs ../api/js/cli/externs_web.js --js_output_file $@ $^

# Generated by genmake.py
LESSC:=../api/build/node/bin/lessc --strict-imports --strict-math=on --strict-units=on
build/less/mitro_popup2.css: frontend/static/less/mitro_popup2.less | build/less
	$(LESSC) $< $@
	$(LESSC) $< $@ --depends > build/less/mitro_popup2.css.dep
-include build/less/mitro_popup2.css.dep

$(BUILD_DIR)/chrome/debug/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/chrome/debug/css
	cp $< $@
$(BUILD_DIR)/chrome/release/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/chrome/release/css
	cp $< $@
$(BUILD_DIR)/firefox/debug/data/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/firefox/debug/data/css
	cp $< $@
$(BUILD_DIR)/firefox/release/data/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/firefox/release/data/css
	cp $< $@
$(BUILD_DIR)/safari/debug.safariextension/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/safari/debug.safariextension/css
	cp $< $@
$(BUILD_DIR)/safari/release.safariextension/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/safari/release.safariextension/css
	cp $< $@
$(BUILD_DIR)/webpage/release/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/webpage/release/css
	cp $< $@
$(BUILD_DIR)/webpage/debug/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/webpage/debug/css
	cp $< $@
$(BUILD_DIR)/webpage/test/css/mitro_popup2.css: build/less/mitro_popup2.css | $(BUILD_DIR)/webpage/test/css
	cp $< $@

LESSC:=../api/build/node/bin/lessc --strict-imports --strict-math=on --strict-units=on
build/less/site.css: frontend/static/less/site.less | build/less
	$(LESSC) $< $@
	$(LESSC) $< $@ --depends > build/less/site.css.dep
-include build/less/site.css.dep

$(BUILD_DIR)/chrome/debug/css/site.css: build/less/site.css | $(BUILD_DIR)/chrome/debug/css
	cp $< $@
$(BUILD_DIR)/chrome/release/css/site.css: build/less/site.css | $(BUILD_DIR)/chrome/release/css
	cp $< $@
$(BUILD_DIR)/firefox/debug/data/css/site.css: build/less/site.css | $(BUILD_DIR)/firefox/debug/data/css
	cp $< $@
$(BUILD_DIR)/firefox/release/data/css/site.css: build/less/site.css | $(BUILD_DIR)/firefox/release/data/css
	cp $< $@
$(BUILD_DIR)/safari/debug.safariextension/css/site.css: build/less/site.css | $(BUILD_DIR)/safari/debug.safariextension/css
	cp $< $@
$(BUILD_DIR)/safari/release.safariextension/css/site.css: build/less/site.css | $(BUILD_DIR)/safari/release.safariextension/css
	cp $< $@
$(BUILD_DIR)/webpage/release/css/site.css: build/less/site.css | $(BUILD_DIR)/webpage/release/css
	cp $< $@
$(BUILD_DIR)/webpage/debug/css/site.css: build/less/site.css | $(BUILD_DIR)/webpage/debug/css
	cp $< $@
$(BUILD_DIR)/webpage/test/css/site.css: build/less/site.css | $(BUILD_DIR)/webpage/test/css
	cp $< $@


# Output directories
build/less:
	mkdir -p $@
