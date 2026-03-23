export interface Model {
    key: string;
    message: string;
}
export class Constants {
    static readonly EAMModels: Model[] = [
        { key: 'PrereqConfig', message: 'Creating EAM Dashboard Prerequisites' },
        { key: 'Material', message: 'Creating Material Model' },
        { key: 'Equipment', message: 'Creating Equipment Model' },
        { key: 'MaintenanceOrder', message: 'Creating Maintenance Order Model' },
        { key: 'MaintenanceNotification', message: 'Creating Maintenance Notification Model' },
        { key: 'FunctionalLocation', message: 'Creating Functional Location Model' }
		
    ];
    static readonly PWModels: Model[] = [
        { key: 'PWModel1', message: 'Creating PWModel1 Model.' },
        { key: 'PWModel2', message: 'Creating PWModel2 Model.' },
        { key: 'PWModel3', message: 'Creating PWModel3 Model.' },
        { key: 'PWModel4', message: 'Creating PWModel4 Model.' },
        { key: 'PWModel5', message: 'Creating PWModel5 Model.' }
    ];
}