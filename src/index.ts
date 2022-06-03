#!/usr/bin/env node
"use strict"

import {Marksync} from "./marksync/marksync";

process.on("uncaughtException", e => {
    process.stderr.write(`${e}\n`);
    process.exit(1);
});
process.on("unhandledRejection", e => {
    process.stderr.write(`${e}\n`);
    process.exit(1);
});

// noinspection JSIgnoredPromiseFromCall
(new Marksync).run();
