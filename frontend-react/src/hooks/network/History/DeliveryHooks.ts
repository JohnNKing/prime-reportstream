import { useCallback, useMemo } from "react";

import { useAdminSafeOrgName } from "../UseMemoizedConfig";
import { useAuthorizedFetch } from "../../../contexts/AuthorizedFetchContext";
import {
    deliveriesEndpoints,
    RSDelivery,
    RSFacility,
} from "../../../config/endpoints/deliveries";
import { Filters } from "../../filters/UseFilterManager";

const { getOrgDeliveries, getDeliveryDetails, getDeliveryFacilities } =
    deliveriesEndpoints;

/** Hook consumes the ReportsApi "list" endpoint and delivers the response
 *
 * @param org {string} the `parsedName` of user's active membership
 * @param service {string} the chosen receiver service (e.x. `elr-secondary`)
 * @param filters {Filters} the filters set by a user in the UI
 * */
const useOrgDeliveries = (
    org?: string,
    service?: string,
    filters?: Filters
) => {
    const { authorizedFetch } = useAuthorizedFetch<RSDelivery[]>();

    const adminSafeOrgName = useAdminSafeOrgName(org); // "PrimeAdmins" -> "ignore"
    const orgAndService = useMemo(
        () => `${adminSafeOrgName}.${service}`,
        [adminSafeOrgName, service]
    ); // ex: xx-phd.elr
    const memoizedDataFetch = useCallback(
        () =>
            authorizedFetch(getOrgDeliveries, {
                segments: {
                    orgAndService,
                },
                params: {
                    sortDir: filters?.order,
                    since: filters?.from,
                    until: filters?.to,
                },
            }),
        [authorizedFetch, orgAndService, filters]
    );
    return { fetchDeliveriesList: memoizedDataFetch };
};

/** Hook consumes the ReportsApi "detail" endpoint and delivers the response
 *
 * @param id {string} Pass in the reportId to query a single delivery
 * */
const useReportsDetail = (id: string) => {
    const { authorizedFetch, rsUseQuery } = useAuthorizedFetch<RSDelivery>();
    const memoizedDataFetch = useCallback(
        () =>
            authorizedFetch(getDeliveryDetails, {
                segments: {
                    id: id,
                },
            }),
        [authorizedFetch, id]
    );
    const { data } = rsUseQuery(
        // sets key with orgAndService so multiple queries can be cached when viewing multiple detail pages
        // during use
        [getDeliveryDetails.queryKey, id],
        memoizedDataFetch,
        { enabled: !!id }
    );
    return { reportDetail: data };
};

/** Hook consumes the ReportsApi "detail" endpoint and delivers the response
 *
 * @param id {string} Pass in the reportId to query for facilities on a report
 * */
const useReportsFacilities = (id: string) => {
    const { authorizedFetch, rsUseQuery } = useAuthorizedFetch<RSFacility[]>();
    const memoizedDataFetch = useCallback(
        () =>
            authorizedFetch(getDeliveryFacilities, {
                segments: {
                    id: id,
                },
            }),
        [authorizedFetch, id]
    );
    const { data } = rsUseQuery(
        // sets key with orgAndService so multiple queries can be cached when viewing multiple detail pages
        // during use
        [getDeliveryFacilities.queryKey, id],
        memoizedDataFetch,
        { enabled: !!id }
    );
    return { reportFacilities: data };
};

export { useOrgDeliveries, useReportsDetail, useReportsFacilities };
