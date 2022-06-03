import {FileInfo} from "../../../src/marksync/uploader/file";

describe("FileInfo", () => {
    test("sha1", () => {
        const result = FileInfo.sha1("tests/resources/doc/test1/image1.png");
        expect(result).toBe("9361921e06fd7d90b7215bee2fb25f8359f2f5e6");
    })

    test("isIdenticalToTrue", () => {
        const fileInfo = new FileInfo({filename: "image1.png", digest: "9361921e06fd7d90b7215bee2fb25f8359f2f5e6"});
        expect(fileInfo.isIdenticalTo("tests/resources/doc/test1/image1.png")).toBeTruthy();
    })

    test("isIdenticalToFalse", () => {
        const fileInfo = new FileInfo({filename: "image1.png", digest: "9361921e06fd7d90b7215bee2fb25f8359f2f5e5"});
        expect(fileInfo.isIdenticalTo("tests/resources/doc/test1/image1.png")).toBeFalsy();
    })
})
