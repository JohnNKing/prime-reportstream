/* eslint-disable react-hooks/rules-of-hooks */
import { SuspenseQueryHook, Middleware, Fetcher } from "react-query-kit";
import axios, { AxiosRequestConfig } from "axios";

import { useSessionContext } from "../contexts/Session";
import { useAppInsightsContext } from "../contexts/AppInsights";
import { RSEndpoint } from "../config/endpoints";

export type AuthMiddleware<TData> = Middleware<
    SuspenseQueryHook<TData, FetchVariables>
>;

/**
 * react-query middleware that prepares the fetch configuration (dynamic url, auth etc.)
 * from an expected RSEndpoint variable. Will disable the query if it cannot pass on
 * a fetchConfig (either built here or given).
 */
export const authMiddleware: Middleware<
    SuspenseQueryHook<unknown, FetchVariables>
> = (useQueryNext) => {
    return (options, qc) => {
        if (!options.variables?.endpoint) throw new Error("Endpoint not found");
        const { fetchHeaders } = useAppInsightsContext();
        const { authState, activeMembership } = useSessionContext();
        const authHeaders = {
            ...fetchHeaders(),
            "authentication-type": "okta",
            authorization: `Bearer ${
                authState?.accessToken?.accessToken ?? ""
            }`,
            organization: `${activeMembership?.parsedName ?? ""}`,
        };
        const headers = {
            ...authHeaders,
            ...options.variables.fetchConfig?.headers,
        };
        const axiosConfig = options.variables.endpoint.toAxiosConfig({
            ...options.variables.fetchConfig,
            headers,
        });
        const fetchConfig =
            options.variables?.fetchConfig || axiosConfig
                ? {
                      ...options.variables?.fetchConfig,
                      ...axiosConfig,
                      enabled: options.variables?.fetchConfig?.enabled == null,
                  }
                : undefined;
        const newOptions = {
            ...options,
            variables: {
                ...options.variables,
                fetchConfig,
            },
        };
        return useQueryNext(newOptions, qc);
    };
};

export type FetchVariables = {
    endpoint: RSEndpoint;
    fetchConfig?: Partial<AxiosRequestConfig> & { enabled?: boolean };
};
export type AuthFetch<TData> = Fetcher<TData, FetchVariables>;

/**
 * Calls fetch with the provided fetch config from variables.
 */
export const authFetch: Fetcher<unknown, FetchVariables> = async ({
    fetchConfig,
}) => {
    if (!fetchConfig) throw new Error("Fetch config not found");

    if (fetchConfig.enabled === false) return Promise.resolve(null);

    const res = await axios<unknown>(fetchConfig);
    return res.data;
};

/**
 * Convenience function to get queryFn and middleware for auth that
 * can be typed. THE MIDDLEWARE MUST COME AFTER OTHER MIDDLEWARE THAT
 * SUPPLIES THE ENDPOINT.
 */
export function getAuthFetchProps<TData>() {
    return {
        authFetch: authFetch as AuthFetch<TData>,
        authMiddleware: authMiddleware as AuthMiddleware<TData>,
    };
}
