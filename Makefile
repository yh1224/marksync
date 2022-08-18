all: build

.PHONY: build
build: node_modules
	npm run build

.PHONY: test
test:
	npm run test

.PHONY: node_modules
node_modules:
	npm ci

.PHONY: clean
clean:
	rm -fr node_modules

VERSION ?=

.PHONY: publish
publish:
ifeq ($(VERSION),)
	$(error "VERSION required.")
endif
	sed -i.bak "s/0.0.0/$(VERSION)/" package.json
	grep "\"version\": \"$(VERSION)\"," package.json
	@$(MAKE) clean build test
	@echo "Publishing version: $(VERSION)"
	npm publish
