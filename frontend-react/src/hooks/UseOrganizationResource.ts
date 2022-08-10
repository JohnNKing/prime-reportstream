import { useMemo } from "react";

import { useSessionContext } from "../contexts/SessionContext";
import { createRequestConfig } from "../network/api/NewApi";
import OrganizationsAPI from "../network/api/OrganizationsApi";

import useRequestConfig from "./network/UseRequestConfig";

export const useOrganizationResource = () => {
    /* Access the session. */
    const { memberships, oktaToken } = useSessionContext();
    /* Create a stable config reference with useMemo(). */
    const config = useMemo(
        () =>
            createRequestConfig<{ org: string }>(
                OrganizationsAPI,
                "detail",
                "GET",
                oktaToken?.accessToken,
                memberships.state.active?.parsedName,
                {
                    org: memberships.state.active?.parsedName || "",
                }
            ),
        /* Note: we DO want to update config ONLY when these values update. If the linter
         * yells about a value you don't want to add, add an eslint-ignore comment. */
        [oktaToken?.accessToken, memberships.state.active]
    );
    /* Pass the stable config into the consumer and cast the response with types. */
    const {
        data: organization,
        error,
        loading,
    } = useRequestConfig(config) as {
        data: any; // TODO (#5892): Should return Newable object or array of Newable objects.
        error: string;
        loading: boolean;
    };
    /* Finally, return the values from the hook. */
    return {
        organization,
        error,
        loading,
    };
};
