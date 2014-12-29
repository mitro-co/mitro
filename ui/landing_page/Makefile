BUILD_DIR=build
TEMPLATES_DIR=templates
LESS_DIR=less

TEMPLATE_FILES=$(wildcard $(TEMPLATES_DIR)/*.mustache) $(wildcard $(TEMPLATES_DIR)/openaccess/*)
HTML_FILES=$(TEMPLATE_FILES:$(TEMPLATES_DIR)%mustache=$(BUILD_DIR)%html)
LESS_FILES=$(wildcard $(LESS_DIR)/*.less)
CSS_FILES=$(foreach file,$(LESS_FILES),$(BUILD_DIR)/css/$(notdir $(basename $(file))).css)

STATIC_DIRS=css js img firefox safari fonts mitro-web-alpha new-ui
OUTPUT_DIRS=$(foreach d,$(STATIC_DIRS) $(LESS_DIR) openaccess,$(BUILD_DIR)/$(d)) $(BUILD_DIR)

MUSTACHE_RENDERER:=$(abspath mustache_renderer.js)
LESSC:=../tools/node/bin/lessc --strict-imports --strict-math=on --strict-units=on

define MKDIR_RULE
$(1):
	mkdir -p $(1)
endef

define COPY_RULE
$(2): $(1) | $(dir $(2))
  @echo $(2)
  @cp $(1) $(2)
endef

define MUSTACHE_RENDERER_RULE
$(2): $(1) $(dir $(2)) $(HEADER_FOOTER_FILES)
	@echo $(2)
	@cd $(dir $(1)) && node $(MUSTACHE_RENDERER) $(notdir $(1)) $(abspath $(2))
endef

define LESS_RULE
$(2): $(1) | $(dir $(2))
	@echo $(2)
	$(LESSC) $(1) $(2)
	$(LESSC) $(1) $(2) --depends > $(2).dep
-include $(2).dep
endef

all: release

release: $(HTML_FILES) $(CSS_FILES) $(BUILD_DIR)/robots.txt $(BUILD_DIR)/static/new/app | $(BUILD_DIR)
	# -R: Copy links as links
	cp -R $(STATIC_DIRS) $(BUILD_DIR)
	# We need /static/html/install.html for backwards compatibility.
	mkdir -p $(BUILD_DIR)/static/html
	# This *must* be relative so it works on the web server
	ln -sf ../../install.html $(BUILD_DIR)/static/html/install.html

clean:
	rm -rf $(BUILD_DIR)

$(BUILD_DIR)/static: | $(BUILD_DIR)
	mkdir -p $@

$(BUILD_DIR)/static/new/app: | $(BUILD_DIR)/static
	# Used for the two-factor auth pages in the app; maybe nothing else?
	mkdir -p $(BUILD_DIR)/static/new
	rsync -rv app $(BUILD_DIR)/static/new

$(BUILD_DIR)/robots.txt: robots.txt
	cp $< $@

$(foreach d,$(OUTPUT_DIRS),$(eval $(call MKDIR_RULE,$(d))))

$(foreach file,$(HTML_FILES),$(eval $(call MUSTACHE_RENDERER_RULE,$(patsubst $(BUILD_DIR)%.html,$(TEMPLATES_DIR)%.mustache,$(file)),$(file))))

$(foreach file,$(CSS_FILES),$(eval $(call LESS_RULE,$(LESS_DIR)/$(notdir $(basename $(file))).less,$(file))))

.PHONY: all release clean
