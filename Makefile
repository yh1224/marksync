all: build

.PHONY: build
build: node_modules
	pnpm run build

.PHONY: test
test:
	pnpm run test

.PHONY: node_modules
node_modules:
	pnpm install --frozen-lockfile

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
	pnpm publish --no-git-checks
