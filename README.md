# marksync

Synchronize Markdown documents to services.

Supported services are:

- [Qiita](https://qiita.com)
- [esa](https://esa.io)

## Prerequisite

- [Ammonite](https://ammonite.io)

    ex) on macOS

    ```bash
    $ brew install ammonite-repl
    ```

## How to use

### Setup

Place .env file.

```bash
$ cp .env.example.qiita .env
```

### Write

Place index.md and metadata(qiita.yml or esa-{teamName}.yml) into one folder.

See [example](example) folder.

### Check modified

```bash
$ amm marksync.sc check <target-path>
```

### Update

```bash
$ amm marksync.sc update <target-path>
```

## Fetch all documents from service

You can fetch documents from service.

```bash
$ amm marksync.sc fetch <output-path>
```
