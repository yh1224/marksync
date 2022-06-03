export interface IQiitaItemTag {
    readonly name?: string;
    readonly versions?: string[];
}

export class QiitaItemTag {
    public readonly name: string;
    public readonly versions: string[] = [];

    constructor(data: IQiitaItemTag) {
        this.name = data.name!;
        if (data.versions !== undefined) this.versions = data.versions;
    }

    toString(): string {
        return `QiitaItemTag(name=${this.name}, versions=[${this.versions.join(", ")}])`;
    }
}
