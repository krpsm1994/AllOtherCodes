import { Component, HostListener, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { RestCallService } from 'src/app/services/rest-call.service';
import { HttpHeaders } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { LoggingService } from 'src/app/services/logging.service';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

declare let $: any;

@Component({
    selector: 'upload-taxonomy',
    standalone: true,
    imports: [CommonModule, FormsModule],
    templateUrl: './upload-taxonomy.component.html',
    styleUrls: ['./upload-taxonomy.component.less'],
    encapsulation: ViewEncapsulation.None
})
export class UploadTaxonomy implements OnInit {

    @ViewChild('fileInput', { static: false }) fileInput: any;

    taxonomies = ['Fashion Taxonomy', 'Fashion Color Taxonomy'];
    delimiters = ['Comma (,)', 'TAB', 'Pipe (|)', 'Semicolon (;)'];
    fileExtensions = '';
    taxonomyFile: any = undefined;
    message: string = '';

    selectedTaxonomy: string = '';
    taxonomyDropdownOpen: boolean = false;

    selectedDelimiter: string = '';
    delimiterDropdownOpen: boolean = false;

    enableGroceryFields: boolean = false;
    reviewedPreferredAttributes = false;

    constructor(
        private restService: RestCallService,
        private toastr: ToastrService,
        private log: LoggingService
    ) { }

    ngOnInit(): void {
        this.clear();
    }

    toggleTaxonomyDropdown() {
        this.taxonomyDropdownOpen = !this.taxonomyDropdownOpen;
    }

    selectTaxonomy(taxonomy: string) {
        this.selectedTaxonomy = taxonomy;
        this.enableGroceryFields = false;
        this.fileExtensions = ".xls,.csv";
        this.taxonomyDropdownOpen = false;
        this.clearSelectedFile();
    }

    toggledelimiterDropdown() {
        this.delimiterDropdownOpen = !this.delimiterDropdownOpen;
    }

    selectDelimiter(delimiter: string) {
        this.selectedDelimiter = delimiter;
        this.delimiterDropdownOpen = false;
    }

    onFileSelected(event: any) {
        let selectedFile = event.target.files[0];
        if (selectedFile) {
            const fileExtension = selectedFile.name.split('.').pop().toLowerCase();
            if (fileExtension === 'xls' || (this.enableGroceryFields && fileExtension === 'xlsx') ||
                (!this.enableGroceryFields && fileExtension === 'csv')) {
                this.taxonomyFile = event.target.files[0];
            } else {
                this.message = 'Invalid file format provided. Supported formats are CSV and XLS.';
                this.toastr.error(this.message);
                this.clearSelectedFile();
            }
        }
    }

    closeAllDropdowns(event: any) {
        if (event.target.id !== 'otdcpw-taxonomyDropdown' && event.target.id !== 'otdcpw-selectedTaxonomy' && event.target.id !== 'otdcpw-selectedTaxonomyArrow') {
            this.taxonomyDropdownOpen = false;
        }
        if (event.target.id !== 'otdcpw-delimiterDropdown' && event.target.id !== 'otdcpw-selectedDelimiter' && event.target.id !== 'otdcpw-selectedDelimiterArrow') {
            this.delimiterDropdownOpen = false;
        }
    }

    closeConfirmationModal() {
        $('#confirmationModal').modal('hide');
    }

    openConfirmationModal() {
        $('#confirmationModal').modal('show');
    }

    navigateToReview() {
        $('#confirmationModal').modal('hide');
        //this.router.navigateByUrl('/groceryAttributes');
    }

    onAlreadyReviewed() {
        this.reviewedPreferredAttributes = true;
        $('#confirmationModal').modal('hide');
        this.saveTaxonomy();
    }

    saveTaxonomy() {
        const fileInput = this.fileInput.nativeElement;
        if (fileInput.files.length === 0) {
            this.message = 'Please select a source file.';
            this.toastr.error(this.message);
            return;
        }

        const selectedFile = fileInput.files[0];
        const formData = new FormData();

        if (selectedFile instanceof File && selectedFile.size > 0) {
            formData.append('taxonomyFile', selectedFile);
        }
        formData.append('fileName', selectedFile.name);
        formData.append('taxonomyType', this.selectedTaxonomy);

        formData.append('delimiter', this.selectedDelimiter);

        let headers: HttpHeaders = new HttpHeaders();
        headers.append("Content-Type", "multipart/form-data");
        headers.append("Accept", "application/json");

        this.restService.post('', "/ai/taxonomy", formData, headers).subscribe({
            next: () => {
                this.message = "Taxonomy updated successfully."
                this.log.debug(this.message);
                this.toastr.success(this.message);
            },
            error: (error: any) => {
                if (error) {
                    this.message = error;
                } else {
                    this.message = 'Unable to upload taxonomy. Please try again.';
                }
                this.toastr.error(this.message);
            }
        });
        this.reviewedPreferredAttributes = false;
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: any) {
        this.closeAllDropdowns(event);
    }

    clearSelectedFile() {
        let sourceFileElement = document.getElementById("otdcpw-taxonomy-file") as HTMLInputElement;
        if (sourceFileElement) {
            sourceFileElement.value = '';
        }
    }

    clear() {
        this.selectedTaxonomy = this.taxonomies[0];
        this.taxonomyDropdownOpen = false;
        this.selectedDelimiter = this.delimiters[0];
        this.delimiterDropdownOpen = false;
        this.taxonomyFile = undefined;
        this.message = '';
        this.enableGroceryFields = false;
        this.reviewedPreferredAttributes = false;
        this.clearSelectedFile();
    }
}