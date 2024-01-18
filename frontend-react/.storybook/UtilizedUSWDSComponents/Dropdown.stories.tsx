import { Dropdown, Label } from "@trussworks/react-uswds";
import React, { ReactElement } from "react";

export default {
    title: "Components/Dropdown",
    component: "Dropdown",
};

export const defaultDropdown = (): ReactElement => (
    <Dropdown id="input-dropdown" name="input-dropdown">
        <option>- Select - </option>
        <option value="value1">Option A</option>
        <option value="value2">Option B</option>
        <option value="value3">Option C</option>
    </Dropdown>
);

export const withDefaultValue = (): ReactElement => (
    <Dropdown id="input-dropdown" name="input-dropdown" defaultValue="value2">
        <option>- Select - </option>
        <option value="value1">Option A</option>
        <option value="value2">Option B</option>
        <option value="value3">Option C</option>
    </Dropdown>
);

export const withLabel = (): ReactElement => (
    <>
        <Label htmlFor="options">Dropdown label</Label>
        <Dropdown id="input-dropdown" name="input-dropdown">
            <option>- Select - </option>
            <option value="value1">Option A</option>
            <option value="value2">Option B</option>
            <option value="value3">Option C</option>
        </Dropdown>
    </>
);

export const disabled = (): ReactElement => (
    <Dropdown id="input-dropdown" name="input-dropdown" disabled>
        <option>- Select - </option>
        <option value="value1">Option A</option>
        <option value="value2">Option B</option>
        <option value="value3">Option C</option>
    </Dropdown>
);
