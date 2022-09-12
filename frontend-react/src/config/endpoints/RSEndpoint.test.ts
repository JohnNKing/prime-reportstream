import { RSEndpoint } from ".";

const testEndpoint = new RSEndpoint({
    path: "/path",
    method: "GET",
    queryKey: "a query key",
});

const dynamicEndpoint = new RSEndpoint({
    path: "/:something/:anything",
    method: "GET",
});

describe("RSEndpoint", () => {
    test("instantiates with expected values", () => {
        expect(testEndpoint.path).toEqual("/path");
        expect(testEndpoint.queryKey).toEqual("a query key");
        expect(testEndpoint.method).toEqual("GET");
    });

    test("has a getter for url that works", () => {
        expect(testEndpoint.url).toEqual("https://test.prime.cdc.gov/api/path");
    });

    describe("toDynamicUrl", () => {
        test("throws if endpoint has dynamic segments and no values are passed", () => {
            expect(() => dynamicEndpoint.toDynamicUrl()).toThrow();
        });

        test("returns url if no dynamic segments are present and no values are passed", () => {
            expect(testEndpoint.toDynamicUrl()).toEqual(
                "https://test.prime.cdc.gov/api/path"
            );
        });

        test("throws if not all dynamic segments are provided with values", () => {
            expect(() =>
                dynamicEndpoint.toDynamicUrl({ something: "else" })
            ).toThrow();
        });

        test("replaces all dynamic segments with provided values", () => {
            expect(
                dynamicEndpoint.toDynamicUrl({
                    something: "else",
                    anything: "more",
                })
            ).toEqual("https://test.prime.cdc.gov/api/else/more");
        });
    });

    describe("toAxiosConfig", () => {
        test("passes along key params from class", () => {
            expect(testEndpoint.toAxiosConfig({})).toEqual({
                url: "https://test.prime.cdc.gov/api/path",
                method: "GET",
            });
        });
        test("passes along additional options", () => {
            expect(
                testEndpoint.toAxiosConfig({
                    headers: { "x-fake-header": "anyway" },
                })
            ).toEqual({
                url: "https://test.prime.cdc.gov/api/path",
                method: "GET",
                headers: {
                    "x-fake-header": "anyway",
                },
            });
        });
        test("does not overwrite key params with options", () => {
            expect(
                testEndpoint.toAxiosConfig({
                    headers: { "x-fake-header": "anyway" },
                    url: "do not use",
                    method: "POST",
                })
            ).toEqual({
                url: "https://test.prime.cdc.gov/api/path",
                method: "GET",
                headers: {
                    "x-fake-header": "anyway",
                },
            });
        });
        test("does not pass through segments data", () => {
            expect(
                dynamicEndpoint.toAxiosConfig({
                    headers: { "x-fake-header": "anyway" },
                    segments: {
                        something: "else",
                        anything: "more",
                    },
                })
            ).toEqual({
                url: "https://test.prime.cdc.gov/api/else/more",
                method: "GET",
                headers: {
                    "x-fake-header": "anyway",
                },
            });
        });
    });
});
