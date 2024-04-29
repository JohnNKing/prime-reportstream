import { expect, Page } from "@playwright/test";
import fs from "node:fs";
import { fromDateWithTime, toDateWithTime } from "../pages/daily-data";

export const TEST_ORG_IGNORE = "ignore";
export async function scrollToFooter(page: Page) {
    // Scrolling to the bottom of the page
    await page.locator("footer").scrollIntoViewIfNeeded();
}

export async function scrollToTop(page: Page) {
    // Scroll to the top of the page
    await page.evaluate(() => window.scrollTo(0, 0));
}

export async function waitForAPIResponse(page: Page, requestUrl: string) {
    const response = await page.waitForResponse((response) =>
        response.url().includes(requestUrl),
    );
    return response.status();
}

export function getTableRows(page: Page) {
    return page.locator(".usa-table tbody").locator("tr");
}

export async function selectTestOrg(page: Page) {
    await page.goto("/admin/settings", {
        waitUntil: "domcontentloaded",
    });

    await waitForAPIResponse(page, "/api/settings/organizations");

    await page.getByTestId("gridContainer").waitFor({ state: "visible" });
    await page.getByTestId("textInput").fill(TEST_ORG_IGNORE);
    await page.getByTestId("ignore_set").click();
}

export async function tableData(
    page: Page,
    row: number,
    column: number,
    expectedData: string,
) {
    await expect(
        getTableRows(page).nth(row).locator("td").nth(column),
    ).toHaveText(expectedData);
}

/**
 * Save session storage to file. Session storage is not handled in
 * playwright's storagestate.
 */
export async function saveSessionStorage(userType: string, page: Page) {
    const sessionJson = await page.evaluate(() =>
        JSON.stringify(sessionStorage),
    );
    fs.writeFileSync(
        `e2e/.auth/${userType}-session.json`,
        sessionJson,
        "utf-8",
    );
}

export async function restoreSessionStorage(userType: string, page: Page) {
    const session = JSON.parse(
        fs.readFileSync(`e2e/.auth/${userType}-session.json`, "utf-8"),
    );
    await page.context().addInitScript((session) => {
        for (const [key, value] of Object.entries<any>(session))
            window.sessionStorage.setItem(key, value);
    }, session);
}

export async function expectTableColumnValues(
    page: Page,
    columnNumber: number,
    expectedValue: string,
) {
    const rowCount = await getTableRows(page).count();

    for (let i = 0; i < rowCount; i++) {
        const columnValue = await getTableRows(page)
            .nth(i)
            .locator("td")
            .nth(columnNumber)
            .innerText();
        expect(columnValue).toContain(expectedValue);
    }
}

export async function expectTableColumnDateTimeInRange(
    page: Page,
    columnNumber: number,
    fromDate: string,
    toDate: string,
    startTime: string,
    endTime: string,
) {
    let areDatesInRange = true;
    const rowCount = await getTableRows(page).count();

    for (let i = 0; i < rowCount; i++) {
        const startDateTime = fromDateWithTime(fromDate, startTime);
        const endDateTime = toDateWithTime(toDate, endTime);
        const columnValue = await getTableRows(page)
            .nth(i)
            .locator("td")
            .nth(columnNumber)
            .innerText();

        const columnDate = new Date(columnValue);

        if (!(columnDate >= startDateTime && columnDate < endDateTime)) {
            areDatesInRange = false;
            break;
        }
    }
    expect(areDatesInRange).toBe(true);
}
