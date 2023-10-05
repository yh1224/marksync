import * as yaml from "js-yaml";
import * as fs from "fs";

export class Mapper {
    static readJson<T>(str: string, aClass: new () => T): T {
        return JSON.parse(str) as T;
    }

    static readJsonFile<T>(filePath: string): T {
        return JSON.parse(fs.readFileSync(filePath).toString()) as T;
    }

    static readYaml<T>(str: string): T {
        return yaml.load(str) as T;
    }

    static toYaml<T>(obj: T): string {
        return yaml.dump(obj, {quotingType: "\"", forceQuotes: true});
    }

    static readYamlFile<T>(filePath: string): T {
        return yaml.load(fs.readFileSync(filePath).toString()) as T;
    }

    static writeYamlFile<T>(filePath: string, obj: T): void {
        fs.writeFileSync(filePath, yaml.dump(obj, {quotingType: "\"", forceQuotes: true}));
    }

    static getJson<T>(obj: T): string {
        return JSON.stringify(obj);
    }
}
