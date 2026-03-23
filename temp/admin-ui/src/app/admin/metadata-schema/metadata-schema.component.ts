import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { CodemirrorModule } from '@ctrl/ngx-codemirror';
import { Subscription } from 'rxjs';
import { LoggingService } from 'src/app/services/logging.service';
import { RestCallService } from 'src/app/services/rest-call.service';

declare let $: any;

@Component({
  selector: 'app-metadata-schema',
  standalone: true,
  imports: [CommonModule, CodemirrorModule, FormsModule],
  templateUrl: './metadata-schema.component.html',
  styleUrls: ['./metadata-schema.component.less'],
  encapsulation: ViewEncapsulation.None
})
export class MetadataSchemaComponent implements OnInit, OnDestroy {

  private createMetdataUrl = '/config/checkAndCreateMetadata';
  private getSchemasURL = '/damuiconfig/schemas';
  private schemaContentUrl = this.getSchemasURL + '/';

  validationError: string;

  isError: boolean;
  errorText: string;
  dataFetched: boolean;
  schemaList = [];
  schemaContent: string;

  private subscriptions: Subscription[] = [];

  codeMirrorOptions = {
    mode: 'application/ld+json',
    lineNumbers: true,
    matchTags: { bothTags: true },
    autoCloseTags: true,
    foldGutter: true,
    gutters: ['CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
    allowDropFileTypes: ["application/json"]
  };

  constructor(private restService: RestCallService,
    private toastr: ToastrService,
    private log: LoggingService) {

  }


  ngOnInit() {
    let schemaNamesSubscription = this.restService.get<any>('', this.getSchemasURL)
      .subscribe({
        next: (response: HttpResponse<any>) => {
          this.dataFetched = false;
          if (response.body) {
            //remove '[', ']', '"' from response.
            this.schemaList = response.body.replace(/[\[\]\"]/g, '').split(',');
          }
        },
        error: (error: Error) => {
          this.log.error('Error during fetching configuration: ', error);
          this.toastr.error('Error during fetching configuration: ' + error);
        }
      });
    this.subscriptions.push(schemaNamesSubscription);
  }

  getSchema(schemaSelected: any): void {
    this.isError = false;
    let schemaSelectedUrl = this.schemaContentUrl + schemaSelected;

    let schemaContentSubscription = this.restService.get<any>('', schemaSelectedUrl)
      .subscribe({
        next: (response: HttpResponse<any>) => {
          this.dataFetched = true;
          if (response.body) {
            // remove '[', ']', '"' from response.
            this.schemaContent = response.body;
          }
        },
        error: (error: Error) => {
          this.log.error('Error during fetching configuration: ', error);
          this.toastr.error('Error during fetching configuration: ' + error);
        }
      });
    this.subscriptions.push(schemaContentSubscription);
  }

  createSchema() {
    this.isError = false;
    let headers: HttpHeaders = new HttpHeaders({ "content-type": "application/json" });
    let createSchemaSubsription = this.restService.put('', this.createMetdataUrl, this.schemaContent, headers)
      .subscribe({
        next: (res) => {
          this.dataFetched = false;
          this.schemaContent = '';
          // For SAPDC-4301 to display message which is coming from api
          this.log.debug(res.body);
          this.toastr.success(res.body);
        },
        error: (error: any) => {
          this.isError = true;
          this.errorText = error;
          this.log.error('Error during creating metadata: ', error);
          this.toastr.error('Error during creating metadata.');
        }
      });
    this.subscriptions.push(createSchemaSubsription);
  }

  dataChanged(event: any) {
    this.dataFetched = true;
    this.isError = false;
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(subscription => {
      subscription.unsubscribe();
    });
  }

}
