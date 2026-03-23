(function (exports) {

    /**
     * @class OTDCPWSyncView
     * @mixes withContent
     * @constructor
     * @param {OTDCPWSyncView} props Properties for this view.
     * @classdesc
     *
     */
    exports.OTDCPWSyncView = otui.extend("AssetMetaDataView", "OTDCPWSyncView", function () {
        this.properties = {
            'name': "OTDCPWSyncView",
            'title': otui.tr("Sync with SAP Commerce Cloud"),
            'type': "OTDCPWSyncView",
            'productData':[]
        };

        this.renderTemplate = function () {
            // If the view is not loaded or rendered.
            //TODO check if this has any significance
            if (!this.internalProperty("loaded")) {
                return;
            }
            
            //Show catalog, product lookup based on hybris connection availability
            if(OTDCPWWorkspaceManager.connection!=""){
				$(".otdcpw-main-catalog").removeClass('otdcpw-main-catalog-nodisplay');
			}

            var selectedMetadataTypeId = OTDCPWWorkspaceManager.model;
            
            var contentArea, metadataJSON = {};

            if (!selectedMetadataTypeId) {
                return;
            }
            var data = { name: selectedMetadataTypeId };
            contentArea = this.contentArea();
            var self = this;
            
            otui.services.selectLookup.read({'lookup' : "OTDC.PW.CATALOGVERSION.LOOKUP", 'asSelect' : false}, function(domainValues){
                var templatefrag = document.createElement("ot-select");
                for (var i = 0; i < domainValues.length; i++)
                {
                    var optionE = document.createElement("option");
                    optionE.value = domainValues[i].value;
                    optionE.text = domainValues[i].displayValue;
                    optionE.setAttribute('type', domainValues[i].type);
                    templatefrag.appendChild(optionE);
                }
                $(".otdcpw-pim-catalog").append(templatefrag);
            });

            otui.MetadataModelManager.read(data, function (containerTypeMetadata) {
                if (!containerTypeMetadata || !containerTypeMetadata.metadata_model_resource.metadata_model) {
                    return;
                }
                var metadataModelIdJSON = { "metadata_model_id" : selectedMetadataTypeId };
                jQuery.extend(true, metadataJSON, metadataModelIdJSON,
                    containerTypeMetadata.metadata_model_resource, containerTypeMetadata.metadata_model_resource.metadata_model);
                var modelEl = contentArea[0].querySelector("ot-model");
                // A folder type might have same model name as that of the earlier selected, hence reset
                // so that metadata view will be re-rendered based on the selected folder type and model,
                // otherwise metadata view will show the content related to the earlier selected folder type
                // in case it has the same model name.
                if (modelEl) {
                    modelEl.model = "";
                }
                // Due to recent changes, "ARTESIA.FIELD.ASSET NAME" field has been made editable.
                // To prevent it from being displayed in UI, setting it in "disabledFields" array.
                modelEl.disabledFields = ["ARTESIA.FIELD.ASSET NAME"];
                otui.Templates.applyObject(contentArea[0], metadataJSON);
                var newFolderMetadataArea = contentArea.find(".ot-new-folder-metadata");
            
                    if (metadataJSON.has_multilingual_fields) {
                    self.handleMultilingualFields(contentArea, modelEl);
                }

                var orientChange = function () {
                    OTDCPWNewWorkspaceView.setHeight(newFolderMetadataArea);
                };
                $(window).on("orientationchange.dialog", orientChange);
                showPreferredFieldsOnly(contentArea,metadataJSON);
            });

            //Set workspace name
            var workspaceName = OTDCPWSyncView.dialogProperties.srcView.internalProperties.asset.name;
            $('.otdcpw-sync-main-txt ot-i18n')[0].textContent = otui.tr('Syncing will overwrite all {0} data with SAP Commerce Cloud data', workspaceName);

        };
        /**
         * Set slim scroll for the rendered view
         */
        this.setSlimScroll = function () {
            var contentArea = this.contentArea();
            var metadataArea = contentArea.find(".ot-new-folder-metadata");
            
            otui.slimscroll(metadataArea, OTDCPWConst.SLIM_SCROLL_CONT_DIMENSIONS);
        };

        this.bind("setup-finished",function(){
            var self = this;
            var inputEl = $(".otdcpw-catalogcol2").find("#otdcpw-create-pim-input");
            var inputFieldId = inputEl[0].id;
            if(inputEl){
                inputEl.typeahead('destroy');
                inputEl.typeahead({
                    hint: true,
                    highlight: true,
                    minLength: OTDCPWWorkspaceManager.config.search.typeahead.minLength,
                    classNames: {
                        input: 'otdcpw-pim-input ot-newfolder-form-element ot-text-input',
                        hint: 'otdcpw-pim-input ot-newfolder-form-element ot-text-input ot-text-input-hint'
                    }
                },
                {
                    name: inputFieldId,
                    source: OTDCPWMetadataView.createTypeAheadQuery(inputFieldId),
                    async: true,
                    limit: OTDCPWWorkspaceManager.config.search.typeahead.maxSuggestions,
                    templates: { 
                        empty: ['<div class="otdcpw-empty-message"><span style="padding:4px">'+otui.tr('No matching results found')+'<span></div>']
                        },
                    display: 'value' 
                });
                // Set placeholder for Search Product in SAP Commerce Cloud
                inputEl.attr('placeholder', otui.tr('Search Product in SAP Commerce Cloud'));
                //On selecting a value, sync the data
                inputEl.bind('typeahead:select typeahead:autocomplete',function(event,option){
                    OTDCPWMetadataView.getProductData(option.id, function(success,response){
                        if(!response || !success){
                            return;
                        }
                        self.properties.productData = response;
                        //Enable sync button - Assuming by default every product will have PK, Article number and Catalog Version
                        $('ot-view[ot-view-type="OTDCPWSyncView"] #ot-newfolder-create-button').removeAttr('disabled');
                        Array.prototype.forEach.call($(".ot-primary-metadata ot-metadata[editable]:not([ot-table-cell]):not(.ot-tabular-single-column-scalar)"), function(el)
                            {
                                var obj = response.find(function(element){return element.id == el.fields[0]});
                                if(obj && obj.value){
                                    var def = OTMetadataElement.types[el.type];
                                    var date,value;
                                    if(el.type == 'date'){
                                        var intDate = parseInt(obj.value);
                                        date = new Date(intDate);
                                        value = date;
                                    }else{
                                        value = obj.value;
                                        value = obj.value.replace( /(<([^>]+)>)/ig, '');
                                    }
                                    def.inputValue.set.call(el,value,value);
                                    el.value = value;
                                }else{
                                    if(obj && ( obj.value == null || obj.value == "")){
                                        // var def = OTMetadataElement.types[el.type];
                                        // def.inputValue.set.call(el,'','');
                                        var editor = el.querySelector(".ot-metadata-content");
                                        if (editor)
                                            editor.textContent = null;
                                    }											
                                }										
                            }
                        );

                    },false);
                });
            }
        });
    });

    /**
     *
     * initiates the create collection dialog.
     *
     * @param data - saved data if any
     * @param options - dialog options
     *
     */
    OTDCPWSyncView.show = function (data, options) {
        data = data || {};
        OTDCPWSyncView.dialogProperties = {
            srcView: data.srcView,
            asset: data.asset
        };
        options = options || {};
        
        otui.dialog("otdcpwsyncdialog", options);
    };
 
    OTDCPWSyncView.handlePimInputChange = function handlePimInputChange(event){
        //When value is cleared in Search input bar, disable Sync button and clear off editor
        if($('ot-view[ot-view-type="OTDCPWSyncView"] #otdcpw-create-pim-input')[0].value == ''){
            //Disable sync button
            $('ot-view[ot-view-type="OTDCPWSyncView"] #ot-newfolder-create-button').attr('disabled', 'disabled');
            //Clear the synced values(if any) in editor
            Array.prototype.forEach.call($(".ot-primary-metadata ot-metadata[editable]:not([ot-table-cell]):not(.ot-tabular-single-column-scalar)"), function(el)
            {
                var editor = el.querySelector(".ot-metadata-content");
                if (editor)
                    editor.textContent = null;
            });
        }
    }

    OTDCPWSyncView.handleSync = function (event, data) {
        var dialogElement = event.target;
        var view = otui.Views.containing(dialogElement);
        //Block the main view
        var parentView = OTDCPWSyncView.dialogProperties.srcView;
        parentView.blockContent();
        view.blockContent();
        var metadata = [];
        var productData = view.properties.productData;
        for(var i=0;i<productData.length;i++){
            OTDCPWWorkspaceManager.generateValueforSave(productData[i].value,metadata,productData[i].id);
        }
        //Check if have any data to update
        if(metadata.length){
            var data = {'metadata': metadata};
            var json = {};
            json['edited_folder'] = {'data' : data};
            //Lock asset
            otui.services.asset.lock({'assetID' : OTDCPWSyncView.dialogProperties.srcView.internalProperties.asset.asset_id}, function(response, status, success) {
				if (success)
                    updateMetadata(view,json);
				else {
					var error = (response && response.exception_body) ? response.exception_body.message : otui.tr("Could not lock this asset.");
					otui.NotificationManager.showNotification({
						'message' : error,
						'status' : 'error',
						'stayOpen' : true
					});
                    OTDCPWSyncView.unblockViews(view,parentView);
				}
			});
        }else{
            var error = otui.tr("No data found for sync");
            otui.NotificationManager.showNotification({
                'message' : error,
                'status' : 'error',
                'stayOpen' : true
            });
            OTDCPWSyncView.unblockViews(view,parentView);
        }
    };

    OTDCPWSyncView.unblockViews = function unblockViews(view, parentView){
        view.unblockContent();
        parentView.unblockContent();
         //Close the dialog
        otui.DialogUtils.cancelDialog(view.contentArea()[0], true);
    }

    var updateMetadata = function updateMetadata(self,metadata){
        var folderID = OTDCPWSyncView.dialogProperties.srcView.internalProperties.asset.asset_id;
        var parentView = OTDCPWSyncView.dialogProperties.srcView;
        var assetType = "folder";
        otui.services[assetType].update({'assetID' : folderID, 'modifiedData' : metadata}, function(response, status, success) {
            // There may be scenarios where in the save was successful but post save either the asset may not exist
            // or the user may not have permission to view the asset. For instance, the current user may remove the
            // "view" permission security policy for himself.
            if (!success && status == 404)
            {
                success = true;			
            }

            if (!success && response && response.exception_body) {
                var error = response.exception_body.message;
                otui.NotificationManager.showNotification({
                    'message' : error,
                    'status' : 'error',
                    'stayOpen' : true
                });
            }
            if(success){
                var msg = otui.tr('Successfully synced the SAP Commerce Cloud data');
                var notification = {
                    'message': msg,
                    'status': 'ok',
                    'stayOpen': false
                };
                otui.NotificationManager.showNotification(notification);               
            }
            OTDCPWSyncView.unblockViews(self,parentView);
            //Unlock the asset and route to detail view
            otui.services.asset.unlock({'assetID' : folderID}, function(response, status, success) {
                var params = {};
                params["resourceID"] = folderID;
                parentView.callRoute("show",params,true);  
            });  
                   
        });	
    }

    var showPreferredFieldsOnly = function showPreferredFieldsOnly(contentArea, metadataJSON) {
        //Prepare the hide fields list
        var preferredFieldsList = OTDCPWWorkspaceManager.config.pim.syncFields;
        var hideList = [];
        var fghideList = [];
        var categoryList = metadataJSON.metadata_element_list;
        var matchCount;
        var notDisplayableCount;
        var metadataList;
        var fieldType;
        var field;
        var displayable;
        //Get the fields that are to be hidden based on the show list
        categoryList.forEach(function (category) {
            matchCount=0;
            notDisplayableCount = 0;
            metadataList = category.metadata_element_list;

            for(var idx = 0; idx < metadataList.length; idx++) {
                field = metadataList[idx];
                fieldType = otui.MetadataModelManager.getFieldType(field);

                if(fieldType === "table") {
                    var columnDescriptor = otui.MetadataModelManager.getTableColumns(field.id, field);
                    displayable = columnDescriptor.editable;

                    if(displayable && $.inArray(field.id, preferredFieldsList) === -1 && !columnDescriptor.required) {
                        matchCount++;
                        hideList.push(field.id);
                    }

                    if(!displayable) {
                        notDisplayableCount++;
                    }
                } else {
                    displayable = field.displayable && field.editable;

                    if(displayable && $.inArray(field.id, preferredFieldsList) === -1 && !field.required) {
                        matchCount++;
                        hideList.push(field.id);
                    }

                    if(!displayable) {
                        notDisplayableCount++;
                    }
                }

            }

            if(matchCount+notDisplayableCount === category.metadata_element_list.length) {
                fghideList.push(category.id);
            }
        });
		//Hide fields			
        if(hideList) {
            for(i=0; i < hideList.length; i++) {
                var field = metadataJSON.has_multilingual_fields ? contentArea.find('ot-metadata[ot-fields ="'+ hideList[i]+'"]').parent() : contentArea.find('ot-metadata[ot-fields ="'+ hideList[i]+'"]');
                $(field).hide();
            }
        }
        //Hide field group container as well when all the fields in it are hidden
        if(fghideList) {
            for(i=0; i < fghideList.length; i++) {
                var fieldGroups = contentArea.find("div.ot-metadata-group");

                for(j=0; j < fieldGroups.length; j++) {

                    if($(fieldGroups[j]).attr("group-id") === fghideList[i]) {
                        $(fieldGroups[j]).closest(".ot-metadata-group").hide();
                    }
                }
            }
        }

    }
})(window);