import { HttpResponse } from "@angular/common/http";
import { Model } from "./constants";
import { ToastrService } from "ngx-toastr";
import { LoggingService } from "../services/logging.service";
import { RestCallService } from "../services/rest-call.service";

export class Utilities {

    static finalCallBack: any = undefined;
    static toastr: ToastrService;
    static log: LoggingService;
    static restService: RestCallService;
    static propertiesContext: any;

    static createModels(models: Model[], index: number, configType: string) {
        let model: Model = models[index];
        if (index >= models.length) {
            Utilities.createFETs(configType);
        } else {
            let url = '/workspace/init?type=' + configType + '&action=CreateModel&name=' + model.key
            Utilities.callInitAPI(url, model.message, configType, Utilities.createModels, models, index + 1);
        }
    }

    static createFETs(configType: string) {
        let url = '/workspace/init?type=' + configType + '&action=CreateFETs';
        Utilities.callInitAPI(url, 'Creating Functional Enablement Tokens', configType, Utilities.createFacetConfig);
    }

    static createFacetConfig(configType: string) {
        let url = '/workspace/init?type=' + configType + '&action=CreateFacetConfig';
        Utilities.callInitAPI(url, 'Creating Facet Configuration', configType, Utilities.createRootFolder);
    }

    static createRootFolder(configType: string) {
        let url = '/workspace/init?type=' + configType + '&action=CreateRootFolder';
        Utilities.callInitAPI(url, 'Creating Root Folder', configType, Utilities.finalCallBack, undefined, undefined, true);
    }

    static callInitAPI(url: string, message: string, configType: string, callback?: any, models?: Model[], index?: number, reload?: boolean) {
        Utilities.restService.post(message, url, undefined).subscribe({
            next: (response: HttpResponse<any>) => {
                if (response.ok && callback) {
                    if (reload) {
                        Utilities.toastr.success('Workspace Initialization Is Successfull.');
                        setTimeout(() => { callback(Utilities.propertiesContext); }, 2000);
                    } else if (models && index > -1) {
                        callback(models, index, configType);
                    } else {
                        callback(configType);
                    }
                }
            },
            error: (error: Error) => {
                Utilities.log.error('Error ' + message, error);
                Utilities.toastr.error('Error ' + message + ' ' + error);
            }
        });
    }

    static isValidJson(str: string): { valid: boolean, error?: string } {
        try {
            JSON.parse(str);
            return { valid: true };
        } catch (e) {
            return { valid: false, error: e.message };
        }
    }
}