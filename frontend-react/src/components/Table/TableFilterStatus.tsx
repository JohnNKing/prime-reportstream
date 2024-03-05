export interface TableFilterData {
    resultLength?: number;
    activeFilters: (string | undefined)[];
}

interface TableFilterStatusProps {
    filterStatus: TableFilterData;
}

function TableFilterStatus({ filterStatus }: TableFilterStatusProps) {
    return (
        <div className="margin-left-2">
            <p className="display-inline">
                Showing
                {` (${filterStatus.resultLength ?? 0}) ${filterStatus.resultLength === 1 ? "result" : "results"} `}
                for:{" "}
            </p>

            <p className="display-inline text-bold">
                {filterStatus.activeFilters.filter((item) => item).join(", ")}
            </p>
        </div>
    );
}

export default TableFilterStatus;
