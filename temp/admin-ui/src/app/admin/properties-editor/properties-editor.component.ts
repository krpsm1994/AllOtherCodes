import { debounceTime } from 'rxjs/operators';
import { RestCallService } from 'src/app/services/rest-call.service';
import { Component, OnInit, ViewEncapsulation, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { FormCanDeactivate } from 'src/app/abstract/form-can.deactivate';
import { ToastrService } from 'ngx-toastr';
import { LoggingService } from 'src/app/services/logging.service';
import { HttpResponse } from '@angular/common/http';
import { GlobalVariables } from 'src/app/common/global-variables';
import { ActivatedRoute } from '@angular/router';
import { MdsService } from 'src/app/services/mds.service';
import { Constants, Model } from 'src/app/common/constants';
import { Utilities } from 'src/app/common/utilities';
import { CodemirrorModule } from '@ctrl/ngx-codemirror';

declare let $: any;


@Component({
    selector: 'app-properties-editor',
    standalone: true,
    imports: [CommonModule, FormsModule, RouterModule, CodemirrorModule],
    templateUrl: './properties-editor.component.html',
    styleUrls: ['./properties-editor.component.less'],
    encapsulation: ViewEncapsulation.None
})
export class PropertiesEditor extends FormCanDeactivate implements OnInit, OnDestroy {
    propertiesURL: string;
    header: string;
    originalProperty: string;
    propertiesChanged = new Subject<string>();
    routeData;
    mdsRoute: boolean;
    isEmbedded: boolean | undefined = undefined;
    successMessage: string = '';
    initializeButtonText: string = 'INITIALIZE';

    properties: string;
    isError: boolean;
    errorText: string;
    unSavedData: boolean;
    fetchError: string;
    initializable: boolean = false;
    configType: string;
    mdsBase: string;
    private mdsUrl: string;
    private mdsPwd: string;

    private putRestServiceSubscription: Subscription;
    private configuration: Subscription;
    private getRestServiceSubscription: Subscription;
    private postRestServiceSubscription: Subscription;

    codeMirrorOptions = {
        mode: 'text/x-properties',
        lineNumbers: true,
        matchTags: { bothTags: true },
        autoCloseTags: true,
        foldGutter: true,
        gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter']
    };

    constructor(private restService: RestCallService,
        private toastr: ToastrService,
        private log: LoggingService,
        private route: ActivatedRoute,
        private mdsService: MdsService) {
        super();
        this.configuration = this.propertiesChanged.pipe(
            debounceTime(500)
        ).subscribe(newText => {
            if (newText.replaceAll('\r\n', '\n') !== this.originalProperty.replaceAll('\r\n', '\n')) {
                //Using global variable to show alert message on routing (If user navigates to another page before saving)
                GlobalVariables.unSavedData = true;
                this.unSavedData = true;
            } else {
                GlobalVariables.unSavedData = false;
                this.unSavedData = false;
                this.isError = false;
                this.errorText = '';
            }
        });
    }

    ngOnInit() {
        //During component initialization AI properties file will be fetched
        GlobalVariables.unSavedData = false;
        this.unSavedData = false;
        //append properties value based on routing
        this.routeData = this.route.data.subscribe(data => {
            this.propertiesURL = data.url;
            this.header = data.header;
            this.codeMirrorOptions.mode = data.fileType;
            this.mdsRoute = data.mdsRoute;
            this.successMessage = data.successMessage;
            this.initializable = data.initializable;
            this.configType = data.configType;
        });

        //Fetch config properties
        if (this.mdsRoute) {
            this.isEmbedded = true;
            $('#mdsModal').modal('show');
        } else {
            this.showEmbeddedProperties(this);
        }
    }

    initializeWorkspace() {
        let models: Model[] = [];
        if (this.configType === 'pw') {
            models = Constants.PWModels;
        } else if (this.configType === 'bw') {
            models = Constants.EAMModels;
        }
        Utilities.finalCallBack = this.showEmbeddedProperties;
        Utilities.log = this.log;
        Utilities.toastr = this.toastr;
        Utilities.restService = this.restService;
        Utilities.propertiesContext = this;
        Utilities.createModels(models, 0, this.configType);
    }

    //Open confirmation dialog when user clicks on cancel
    openConfirmationModal() {
        $('#confirmationModal').modal('show');
    }

    //Close confirmation dialog when user clicks on "No" or "X" icon
    closeConfirmationModal() {
        $('#confirmationModal').modal('hide');
    }

    enableButton(text: string): void {
        this.propertiesChanged.next(text);
    }

    onSubmit(form: any) {
        this.isError = false;
        this.errorText = '';
        this.properties = form.configData;
        this.save(this.properties);
    }

    private save(properties: any): void {
        let validJSONResponse: { valid: boolean, error?: string } = { valid: true };
        if (this.propertiesURL.includes('groceryPreferredAttrs')) {
            validJSONResponse = Utilities.isValidJson(properties);
        }
        if (!validJSONResponse.valid) {
            this.errorText = 'Invalid JSON format. ' + validJSONResponse.error;
            this.isError = true;
            return;
        }
        this.putRestServiceSubscription = this.restService.put('', this.propertiesURL, properties).subscribe({
            next: () => {
                GlobalVariables.unSavedData = false;
                this.unSavedData = false;
                this.originalProperty = properties;
                this.log.debug('Configuration saved successfully');
                this.toastr.success('Configuration saved successfully.');
            },
            error: (error: any) => {
                this.isError = true;
                this.errorText = error;
                if (error === 412) {
                    this.errorText = 'Configuration has been modified by different user, copy your changes & refresh to access new content.';
                }
                this.log.error('Error during saving Configuration: ', error);
                this.toastr.error('Error during saving Configuration. See server log.');
            }
        });
    }

    //This method will be called when user clicks "Yes" on confirmation modal
    onYesClick() {
        this.isError = false;
        this.errorText = '';
        this.properties = this.originalProperty;
        GlobalVariables.unSavedData = false;
        this.unSavedData = false;
        $('#confirmationModal').modal('hide');
    }

    showEmbeddedProperties(context: this) {
        GlobalVariables.unSavedData = false;
        context.unSavedData = false;
        context.getRestServiceSubscription = context.restService.get<any>('', context.propertiesURL)
            .subscribe({
                next: (response: HttpResponse<any>) => {
                    context.restService.setETagHeaders = response.headers;
                    if (response.body) {
                        context.properties = response.body;
                        context.originalProperty = response.body;
                        context.log.debug(context.successMessage);
                        context.toastr.info(context.successMessage);
                    } else {
                        context.log.error('Somehow body is empty.');
                        context.toastr.error('Somehow body is empty.');
                    }
                    //Fetch Initialization config for BW
                    context.callWorkspaceConfig();
                },
                error: (error: Error) => {
                    context.log.error('Error during fetching configuration: ', error);
                    context.toastr.error('Error during fetching configuration. Check console or network tab.');
                }
            });


    }

    callWorkspaceConfig() {
        if (this.initializable) {
            this.postRestServiceSubscription = this.restService.post<any>('Loading UI Configuration...', "/workspace/config?type=" + this.configType, null)
                .subscribe({
                    next: (response: HttpResponse<any>) => {
                        let jsonResponse = JSON.parse(response.body);
                        this.initializeButtonText = 'INITIALIZE';
                        if ((jsonResponse.otdcpw && jsonResponse.otdcpw.ui.isInitialized === 'true') ||
                            (jsonResponse.otdcbw && jsonResponse.otdcbw.isInitialized === 'true')) {
                            this.initializeButtonText = 'RE-INITIALIZE';
                        }
                    },
                    error: (error: Error) => {
                        this.log.error('Error loading initialization configuration: ', error);
                        this.toastr.error('Error loading initialization configuration: ' + error);
                    }
                });
        }
    }

    fetchFromRemote() {
        this.isEmbedded = false;
        GlobalVariables.unSavedData = false;
        this.unSavedData = false;
        this.mdsBase = $('#mdsUrl')[0].value;
        this.mdsUrl = this.mdsBase + '/ImConvServlet/imconv';
        this.mdsPwd = $('#mdsPwd')[0].value;

        this.fetchError = '';
        // ok
        this.mdsService.getMDSProps(this.mdsUrl, this.mdsPwd).then(
            (response: string) => {
                $('#mdsModal').modal('hide');
                if (response) {
                    this.properties = response;
                    this.originalProperty = response;
                    this.log.debug(this.successMessage);
                    this.toastr.info(this.successMessage);
                } else {
                    this.log.error('Somehow body is empty.');
                    this.toastr.error('Somehow body is empty.');
                }
            },
            (error: any) => {
                this.fetchError = 'Error happend, retry!! (or check console).';
                this.log.error('Error during fetching configuration check console or network tab).');
                this.toastr.error('Error during fetching configuration check console or network tab.');
            }
        );
    }

    //Close all active subscriptions on component exit
    ngOnDestroy(): void {
        this.configuration.unsubscribe();
        this.properties = '';
        this.originalProperty = '';
        this.propertiesURL = '';
        this.mdsBase = '';
        this.mdsPwd = '';
        if (this.getRestServiceSubscription) {
            this.getRestServiceSubscription.unsubscribe();
        }
        if (this.putRestServiceSubscription) {
            this.putRestServiceSubscription.unsubscribe();
        }
        if (this.routeData) {
            this.routeData.unsubscribe();
        }
    }
}