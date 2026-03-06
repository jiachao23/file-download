export interface GenerateRequest {
    templateId: string;
    params: Record<string, any>;
    targetFormat?: 'WORD' | 'EXCEL' | 'PDF';
    async?: boolean;
}

export interface MockDataItem {
    key: string;
    item: string;
    value: string;
    trend: 'up' | 'down' | 'stable';
    trendValue: string;
}