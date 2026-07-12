export interface GenerateRequest {
    templateId: string;
    params: Record<string, any>;
    targetFormat?: string;
    async?: boolean;
}

export interface ReportTemplate {
    id: string;
    name: string;
    type: 'WORD' | 'EXCEL' | 'PPT';
    content: {
        meta: any;
        components: Component[];
    };
}

export interface Component {
    id: string;
    type: string;
    placeholder: string;
}