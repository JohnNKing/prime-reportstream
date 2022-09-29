import { render, screen } from "@testing-library/react";

import { MembershipSettings, MemberType } from "../hooks/UseOktaMemberships";
import { mockSessionContext } from "../contexts/__mocks__/SessionContext";
import { mockFeatureFlagContext } from "../contexts/__mocks__/FeatureFlagContext";
import { FeatureFlagName } from "../pages/misc/FeatureFlags";

import { AuthElement } from "./AuthElement";

const mockUseNavigate = jest.fn();

jest.mock("react-router", () => ({
    useNavigate: () => mockUseNavigate,
}));

const TestElement = () => <h1>Test Passed</h1>;
const TestElementWithProp = (props: { test: string }) => <h1>{props.test}</h1>;

let mockCheckFlag = jest.fn();

describe("AuthElement unit tests", () => {
    beforeEach(() => {
        mockFeatureFlagContext.mockReturnValue({
            dispatch: () => {},
            checkFlag: () => true,
            featureFlags: [],
        });
    });
    test("Renders component when all checks pass", () => {
        mockCheckFlag = jest.fn((flag) => flag === FeatureFlagName.FOR_TEST);
        mockSessionContext.mockReturnValueOnce({
            oktaToken: {
                accessToken: "TOKEN",
            },
            activeMembership: {
                memberType: MemberType.PRIME_ADMIN,
                parsedName: "PrimeAdmins",
            },
            dispatch: () => {},
            initialized: true,
        });
        mockFeatureFlagContext.mockReturnValue({
            dispatch: () => {},
            checkFlag: mockCheckFlag,
            featureFlags: [],
        });
        render(
            <AuthElement
                element={<TestElementWithProp test={"Success!"} />}
                requiredFeatureFlag={FeatureFlagName.FOR_TEST}
                requiredUserType={MemberType.PRIME_ADMIN}
            />
        );
        expect(screen.getByText("Success!")).toBeInTheDocument();
        expect(mockUseNavigate).not.toHaveBeenCalled();
    });
    test("Redirects when user not logged in (no token, no membership)", () => {
        mockSessionContext.mockReturnValueOnce({
            oktaToken: undefined,
            activeMembership: undefined,
            dispatch: () => {},
            initialized: true,
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredUserType={MemberType.RECEIVER}
            />
        );
        expect(mockUseNavigate).toHaveBeenCalledWith("/login");
    });
    test("Does not redirect when user refreshes app (token loads after membership)", () => {
        mockSessionContext.mockReturnValueOnce({
            oktaToken: undefined,
            activeMembership: {
                memberType: MemberType.SENDER,
                parsedName: "all-in-one-health-ca",
            },
            dispatch: () => {},
            initialized: true,
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredUserType={MemberType.SENDER}
            />
        );
        expect(mockUseNavigate).not.toHaveBeenCalledWith();
        expect(screen.getByText("Test Passed")).toBeInTheDocument();
    });
    test("Redirects when user is unauthorized user type", () => {
        mockSessionContext.mockReturnValueOnce({
            oktaToken: {
                accessToken: "TOKEN",
            },
            activeMembership: {
                memberType: MemberType.SENDER,
                parsedName: "all-in-one-health-ca",
            },
            dispatch: () => {},
            initialized: true,
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredUserType={MemberType.RECEIVER}
            />
        );
        expect(mockUseNavigate).not.toHaveBeenCalledWith("/login"); // Make sure first navigate() doesn't accidentally run
        expect(mockUseNavigate).toHaveBeenCalledWith("/");
    });
    test("Redirects when user lacks feature flag", () => {
        mockCheckFlag = jest.fn((flag) => flag !== FeatureFlagName.FOR_TEST);
        mockSessionContext.mockReturnValueOnce({
            oktaToken: {
                accessToken: "TOKEN",
            },
            activeMembership: {
                memberType: MemberType.SENDER,
                parsedName: "all-in-one-health-ca",
            },
            dispatch: () => {},
            initialized: true,
        });
        mockFeatureFlagContext.mockReturnValue({
            dispatch: () => {},
            checkFlag: mockCheckFlag,
            featureFlags: [],
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredFeatureFlag={FeatureFlagName.FOR_TEST}
            />
        );
        expect(mockUseNavigate).toHaveBeenCalledWith("/");
    });
    test("Considers all given authorized user types (affirmative)", () => {
        mockSessionContext.mockReturnValueOnce({
            oktaToken: {
                accessToken: "TOKEN",
            },
            activeMembership: {
                memberType: MemberType.SENDER,
                parsedName: "PrimeAdmins",
            },
            dispatch: () => {},
            initialized: true,
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredUserType={[MemberType.SENDER, MemberType.RECEIVER]}
            />
        );
        expect(screen.getByText("Test Passed")).toBeInTheDocument();
        expect(mockUseNavigate).not.toHaveBeenCalled();
    });
    test("Considers all given authorized user types (negative)", () => {
        mockSessionContext.mockReturnValueOnce({
            oktaToken: {
                accessToken: "TOKEN",
            },
            activeMembership: {
                memberType: MemberType.NON_STAND,
                parsedName: "PrimeAdmins",
            },
            dispatch: () => {},
            initialized: true,
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredUserType={[MemberType.SENDER, MemberType.RECEIVER]}
            />
        );
        expect(mockUseNavigate).toHaveBeenCalledWith("/");
    });
    test("renders a spinner when user hooks have not initialized", async () => {
        mockSessionContext.mockReturnValueOnce({
            oktaToken: {
                accessToken: "TOKEN",
            },
            activeMembership: {
                memberType: MemberType.NON_STAND,
                parsedName: "PrimeAdmins",
            },
            dispatch: () => {},
            initialized: false,
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredUserType={[MemberType.SENDER, MemberType.RECEIVER]}
            />
        );
        const spinner = await screen.findByTestId("rs-spinner");
        expect(spinner).toBeInTheDocument();
    });
    /* This can happen if a user is a member of multiple Okta orgs, and the one set for them
     * was a non-admin memberType. Because admins are the only ones able to mock memberType, we assign
     * it based on a users first Okta group.
     *
     * In this example, you can see what that session state would look like. */
    test("Permits admins whose active membership is not DHPrimeAdmins", () => {
        mockSessionContext.mockReturnValueOnce({
            oktaToken: {
                accessToken: "TOKEN",
            },
            memberships: new Map<string, MembershipSettings>().set(
                "DHPrimeAdmins",
                {
                    memberType: MemberType.PRIME_ADMIN,
                    parsedName: "PrimeAdmins",
                }
            ),
            activeMembership: {
                memberType: MemberType.RECEIVER,
                parsedName: "xx-phd",
            },
            dispatch: () => {},
            initialized: true,
            adminHardCheck: true,
        });
        render(
            <AuthElement
                element={<TestElement />}
                requiredUserType={MemberType.PRIME_ADMIN}
            />
        );
        expect(screen.getByText("Test Passed")).toBeInTheDocument();
        expect(mockUseNavigate).not.toHaveBeenCalled();
    });
});
