all: build

.PHONY: build
build: node_modules
	npm run build

node_modules:
	npm install

.PHONY: clean
clean:
	./gradlew clean
	rm -fr node_modules
	rm -fr jdeploy-bundle

VERSION ?=

.PHONY: publish
publish: build node_modules
ifeq ($(VERSION),)
	$(error "VERSION required.")
endif
	sed -i.bak "s/SNAPSHOT/$(VERSION)/" build.gradle package.json
	grep "version '$(VERSION)'" build.gradle
	grep "\"version\": \"$(VERSION)\"," package.json
	grep "marksync-$(VERSION).jar" package.json
	@$(MAKE) clean build
	@echo "Publishing version: $(VERSION)"
	npm run publish
