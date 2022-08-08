import { Suspense } from "react";
import { Helmet } from "react-helmet";
import { NetworkErrorBoundary } from "rest-hooks";

import HipaaNotice from "../../components/HipaaNotice";
import Spinner from "../../components/Spinner";
import { useOrgName } from "../../hooks/UseOrgName";
import { ErrorPage } from "../error/ErrorPage";
import Title from "../../components/Title";

import ReportsTable from "./Table/ReportsTable";

function Daily() {
    const orgName: string = useOrgName();
    return (
        <>
            <Helmet>
                <title>Daily data | {process.env.REACT_APP_TITLE}</title>
            </Helmet>
            <section className="grid-container margin-bottom-5 tablet:margin-top-6">
                <Title preTitle={orgName} title="COVID-19" />
            </section>
            <Suspense fallback={<Spinner />}>
                <NetworkErrorBoundary
                    fallbackComponent={(props) => (
                        <ErrorPage type="message" error={props.error} />
                    )}
                >
                    <ReportsTable />
                </NetworkErrorBoundary>
            </Suspense>
            <HipaaNotice />
        </>
    );
}

export default Daily;
