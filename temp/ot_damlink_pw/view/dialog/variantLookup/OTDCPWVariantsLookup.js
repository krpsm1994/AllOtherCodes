(function (exports) {
    /**
     * @class OTDCPWVariantsLookupView
     * @mixes withContent
     * @constructor
     * @param {OTDCPWVariantsLookupView} props Properties for this view.
     * @classdesc
     *
     */
    exports.OTDCPWVariantsLookupView = otui.define("OTDCPWVariantsLookupView", ['withContent'], function () {
        this.properties = {
            'name': "OTDCPWVariantsLookupView",
            'title': otui.tr("Variant Lookup")
        };
        this._initContent = function initOTDCPWVariantsLookupView(self, callback) {
            OTDCPWVariantsLookupView.nodeID = this.properties.nodeID || this.properties.resourceID;
            let template = self.getTemplate("otdcpw-variants-lookup-content");
            $(template.querySelector('div[id="otdcpw-variants-div"]')).append(OTDCPWVariantsLookupView.content)
            callback(template);
        };
    });


    OTDCPWVariantsLookupView.content = undefined;
    OTDCPWVariantsLookupView.show = function (data, workspaceMetadata, event) {
        OTDCPWVariantsLookupView.dialogProperties = {};
        let options = {};
        data = data || {};
        let srcView = otui.Views.containing(event);
        //store parent view details
        OTDCPWVariantsLookupView.dialogProperties = {
            srcView: srcView,
            asset: data.assetID
        };
        options.viewProperties = { "assetInfo": data.assetID };
        //open the dialog
        otui.dialog("otdcpwvariantslookupdialog", options.viewProperties);

        //Get Variants info from Workspace metadata
        let variantsFieldGroup = []
        for (let fg of workspaceMetadata.metadata.metadata_element_list) {
            if (fg.id === 'OTDC.PW.FG.VARIANTS') {
                variantsFieldGroup = fg.metadata_element_list;
            }
        }

        let wrapper = $(event).closest(".ot-tabular-single-scalar-wrapper");

        // Get all selected variants from the ot-chiclet elements within the chiclets section
        let selectedVariants = wrapper.find(".ot-tabular-chiclets-section ot-chiclet").map(function () {
            let otValue = $(this).attr("ot-value"); // Extract ot-value attribute
            let parsedValue = JSON.parse(otValue); // Parse JSON
            return parsedValue.value; // Get 'value' field
        }).get();

        OTDCPWVariantsLookupView.content = OTDCUtilities.generateVariantsTable(variantsFieldGroup, selectedVariants);

    };

    OTDCPWVariantsLookupView.onCheckboxChange = function onCheckboxChange(event, variantCode) {
        event.stopPropagation();
        $.each(OTDCUtilities.variantsJson, function (index, variant) {
            //If selected variant is available in workspace variants list then check the checkbox
            if (OTDCUtilities.sanitizeIdForHtml(variant.code) === variantCode) {
                variant.selected = !variant.selected;
                OTDCUtilities.variantsJson[index] = variant;
            }
        });
        //check and uncheck select all checkbox
        if (OTDCUtilities.checkIfAllVariantsSelected()) {
            $('#otdcpw-select-all-checkbox').prop('checked', true);
        } else {
            $('#otdcpw-select-all-checkbox').prop('checked', false);
        }
    }

    OTDCPWVariantsLookupView.onSelectAllCheckboxChange = function onSelectAllCheckboxChange(event) {
        if (OTDCUtilities.checkIfAllVariantsSelected()) {
            $.each(OTDCUtilities.variantsJson, function (index, variant) {
                variant.selected = false;
                OTDCUtilities.variantsJson[index] = variant;
                $('#otdcpw-select-' + OTDCUtilities.sanitizeIdForHtml(variant.code)).prop('checked', false);
            });
            $('#otdcpw-select-all-checkbox').prop('checked', false);
        } else {
            $.each(OTDCUtilities.variantsJson, function (index, variant) {
                variant.selected = true;
                OTDCUtilities.variantsJson[index] = variant;
                $('#otdcpw-select-' + OTDCUtilities.sanitizeIdForHtml(variant.code)).prop('checked', true);
            });
            $('#otdcpw-select-all-checkbox').prop('checked', true);
        }
    }

    OTDCPWVariantsLookupView.onClickContinue = function onClickContinue(event) {
        let finalSelectedVariants = [];
        //Add items from selectedVariants that are not available in variantsJson
        OTDCUtilities.selectedVariants.forEach(function (selectedVariant) {
            if (!OTDCUtilities.variantsJson.some(variant => variant.code === selectedVariant)) {
                finalSelectedVariants.push(selectedVariant);
            }
        });

        // Add items from variantsJson where "selected" is true
        OTDCUtilities.variantsJson.forEach(function (variant) {
            if (variant.selected) {
                finalSelectedVariants.push(variant.code);
            }
        });
        console.log(finalSelectedVariants);

        let metadataElement = $('ot-metadata[data-type="string"][ot-fields="OTDC.PW.ASSET.PIMVARIANT"]');
        metadataElement.attr({
            "data-request-from-validation": "true",
            "data-is-value-expired": "false"
        });

        // Select the correct chiclet section under ot-fields="OTDC.PW.ASSET.PIMVARIANT"
        let chicletSection = $('ot-metadata[ot-fields="OTDC.PW.ASSET.PIMVARIANT"] .ot-tabular-chiclets-section');

        // Clear existing chiclets
        chicletSection.empty();

        // Loop through finalSelectedVariants and append new chiclets
        finalSelectedVariants.forEach(function (variant) {
            let chicletHtml = `
                <ot-chiclet displayvalue="${variant}" ot-id="${variant}" ot-value="{&quot;value&quot;:&quot;${variant}&quot;}">
                    <div class="ot-chiclet">  
                        <span class="ot-chiclet-value">${variant}</span> 
                        <a ot-onselect="select" class="ot-chiclet-closer"></a> 
                    </div>
                </ot-chiclet>
            `;
            chicletSection.append(chicletHtml);
        });

        //Notify this metadata field is changed by changing dirty property
        let metdataSection = $('ot-metadata[ot-fields="OTDC.PW.ASSET.PIMVARIANT"]');
        metdataSection[0].store.dirty = true;

        //close the dialog
        let dialog = $("[dialogname = 'otdcpwvariantslookupdialog']");
        otui.DialogUtils.cancelDialog(dialog);
    }

})(window);