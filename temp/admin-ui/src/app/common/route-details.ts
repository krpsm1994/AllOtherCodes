export class RouteDetails {
    static readonly damLinkConfig = {
        url: '/damuiconfig/configxml',
        header: 'DAMLink Configuration Editor',
        fileType: 'text/xml',
        mdsRoute: false,
        successMessage: 'Fetched damlink configuration.',
        initializable: false,
        configType: ''
    }
    static readonly productWorkspaceConfig = {
        url: '/damlink/properties?config=pw',
        header: 'Product Workspace Configuration Editor',
        fileType: 'text/x-properties',
        mdsRoute: false,
        successMessage: 'Fetched product workspace configuration.',
        initializable: false,
        configType: 'pw'
    }
    static readonly eamWorkspaceConfig = {
        url: '/damlink/properties?config=bw',
        header: 'EAM Workspace Configuration Editor',
        fileType: 'text/x-properties',
        mdsRoute: false,
        successMessage: 'Fetched EAM workspace configuration.',
        initializable: true,
        configType: 'bw'
    }
    static readonly folderMappingConfig = {
        url: '/damuiconfig/foldermapping',
        header: 'Folder Mapping Configuration Editor',
        fileType: 'text/x-properties',
        mdsRoute: false,
        successMessage: 'Fetched folder mapping configuration.',
        initializable: false,
        configType: ''
    }
    static readonly aiConfig = {
        url: '/damlink/properties?config=ai',
        header: 'AI Configuration Editor',
        fileType: 'text/x-properties',
        mdsRoute: false,
        successMessage: 'Fetched AI configuration.',
        initializable: false,
        configType: ''
    }

    static readonly groceryAttributesConfig={
        url: '/damlink/properties?config=groceryPreferredAttrs',
        header: 'Grocery Preferred Attributes',
        fileType: 'text/x-properties',
        mdsRoute: false,
        successMessage: 'Fetched grocery preferred attributes.',
        initializable: false,
        configType: ''
    }
}