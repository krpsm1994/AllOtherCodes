(function (exports) {
    /**
     * @class OTDCPWAssignToProductView
     * @mixes withContent
     * @constructor
     * @param {OTDCPWAssignToProductView} props Properties for this view.
     * @classdesc
     *
     */			 		 
    exports.OTDCPWAssignToProductView = otui.extend("TableView","OTDCPWAssignToProductView", 		 
    function()
	{
		let pageprop = { 'assetsPerPage': PageManager.assetsPerPage,
						'page': "0",
						'sortFieldforFacets': PageManager.DEAFAULT_CHECKED_OUT_SORT_FIELD,
						'sortField': "NAME",
						'sortPrefix': PageManager.ascendingSortPrefix };
		this.properties =
		{
			'name': "OTDCPWAssignToProductView",
			'title': otui.tr("Assign to Product"),
			'type' : 'assigntoproduct',
			'service' : 'damgetfolders',
			'serviceData' : {},
			'headerTemplate' : 'header',
			'rowTemplate' : 'row',
			'tableSelector' : '.ot-table-content',
			'pageProperties': pageprop,
			'metadataFields':  ["OTDC.PW.DESCRIPTION","OTDC.PW.ARTICLE.NUMBER"], //metadata info required to be fetched during OTDCUtilities.doFolderChildrenRead()
			'appliedFacets': JSON.stringify({'facets': []}),
			'allow-select-all': false, //do not allow page select all as we cannot get the workspaces selected from selection manager
			'showDeletedAssets': "false"//do not show deleted workspaces for assignment
		};
		
		if (otui.is(otui.modes.DESKTOP)){
			this.properties.showPagination = false;
			this.properties.showLatestPagination = true;
			this.properties.showAssetsPerPageSetting = false;
		}
		
		this._initContent = function _initContent(self, callback)
		{
			//Resetting assets per page with latest info in pagemanager
			self.properties.pageProperties.assetsPerPage = PageManager.assetsPerPage;
			//Build custom page ID so that its unique and we use this for pagination
			self.properties.pageID = (OTDCPWAssignToProdWrapper.dialogProperties.selectedAssets) + "_atp";
			if( OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName){
				self.properties.metadataFields = ["OTDC.PW.DESCRIPTION","OTDC.PW.ARTICLE.NUMBER"];
				self.properties.title = otui.tr("Assign to Product");
			} else {
				self.properties.metadataFields = ["OTDCBW.BO.DESC"];
				self.properties.title = otui.tr("Assign to Workspace");
			}
			let contentBuilder = function (location)
			{
				location = $(location);
				callback(location);
				_load.call(self, location);
			};
					
			TableView._initContent.call(this, self, contentBuilder);
		};

		
		this.reload = function reload()
		{
			//Fix for pagination popup issue
			let pageNavSizeOptions = document.getElementsByClassName("show");
			if (pageNavSizeOptions && pageNavSizeOptions.length>0){
				for (const element of pageNavSizeOptions) {
					if (element.id === 'pageNavSizeOptions') {
						element.classList.remove('show');
					}
				}
			}
			let location = this.contentArea();
			_load.call(this, location);
			if(window['OTDCPWSidebarView']) {
				let view = this.internalProperties.parentView.getChildView(OTDCPWSidebarView);
				if(view)
				{
					//Update sidebar facets when view is reloaded, same as Homeview
					view.properties.appliedFacets = this.properties.appliedFacets;
					let facets = document.querySelector(".ot-facets-root .ot-facet");
					if(facets)
					{
						SearchManager.clearSearchForKeyword(this.properties.folderID);
						view.updateFacets();
					}
				}
			}
		}

		this.resultID = function resultID()
		{
			//This is required for pagination
			return this.properties.pageID;
		};

		//This is to create clear button and add it in this view context
		this.createClearButton = function(){
			let div = document.createElement("div");
			div.setAttribute("class","otdcpw-facets-clear-cont");
			let button = document.createElement("button");
			button.setAttribute("class","ot-button secondary");
			button.textContent = otui.tr("Clear");
			button.setAttribute("aria-label",otui.tr("Clear Filters"));
			button.setAttribute("title",otui.tr("Clear Filters"));
			button.addEventListener("click",function(event){
				OTDCPWFacetsView.handleClearFilters();
			});
			div.append(button);
			this.contentArea().find(".otdcpw-atp-tracker-chicklets").append(div);
		}
	});	


	OTDCPWAssignToProductView.onClickFinish = function onClickFinish(event){
		let view = otui.Views.containing(event.target); //Wrapper view
		let contentArea = view.contentArea();
		//Get Selected workspaces
		let selectedWorkspaces = SelectionManager.selections.get(view.getChildView(OTDCPWAssignToProductView)).assetList;
		if(selectedWorkspaces.length === 0){
			//If no workspace selected, throw message
			let errorMsg =  OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName? otui.tr("Please select atleast one product workspace to proceed with assignment."):
otui.tr("Please select atleast one EAM workspace to proceed with assignment.");
			let msg = errorMsg;
            otui.NotificationManager.showNotification({
                message: msg,
                stayOpen: false,
                status: "warning"
            });
			return;
		}
		let maxWorkspacesSelectable = 0;
		if( OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName){
			maxWorkspacesSelectable = OTDCPWWorkspaceManager.config.AssignToProduct.maxWorkspaces;
		} else {
			maxWorkspacesSelectable = OTDCBWWorkspaceManager.config.AssignToWorkspace.maxWorkspaces;
		}

		//Also check if selected workspaces > max workspaces
		if(selectedWorkspaces.length > maxWorkspacesSelectable){
			//throw message
			let msg = otui.tr("Number of Workspaces selected cannot be more than "+ maxWorkspacesSelectable);
            otui.NotificationManager.showNotification({
                message: msg,
                stayOpen: false,
                status: "warning"
            });
			return;
		}
		let assetIdList = OTDCPWAssignToProductView.dialogProperties.selectedAssets;   
        
        let data = {};
        let selectionContext = {
            'load_asset_content_info': true
        };
        data.selection = {
            'data_load_request': selectionContext
        };
        view.internalProperties.successCount = 0;
        view.internalProperties.totalCount = 0;
		 
		//Block the view
		view.blockContent();
		//For each workspace, get its subfolder and assign the assets
		for (const workspace of selectedWorkspaces) {
			let workspaceId = workspace.asset_id;
			// Skip if workspace is the same as the folder/workspace from which ATP is opened
			if (
				OTDCPWAssignToProductView.dialogProperties.folderID &&
				workspaceId === OTDCPWAssignToProductView.dialogProperties.folderID
			) {
				continue;
			}
			data.id = workspaceId;
			//Get subfolder(Media Library or folder maintained in properties file else first sub folder)
			OTDCPWWorkspaceManager.readFolderChildren(data, function callback(response, success) {
				handleReadFolderChildrenResponse(
					response,
					success,
					view,
					selectedWorkspaces.length,
					contentArea,
					assetIdList
				);
			});
		}

		function handleReadFolderChildrenResponse(
			response,
			success,
			view,
			selectedWorkspaceLength,
			contentArea,
			assetIdList
		) {
			if (!success) {
				// Show error notification if folder children could not be read
				otui.NotificationManager.showNotification({
					message: otui.tr("Failed to read workspace folders. Please try again."),
					stayOpen: false,
					status: "error"
				});
				OTDCPWAssignToProductView.getcount(view, selectedWorkspaceLength, contentArea, false);
				return;
			}

			let subFolderId = findSubFolderId(response);
			if (!subFolderId) {
				// Show error notification if subfolder not found
				otui.NotificationManager.showNotification({
					message: otui.tr("No valid subfolder found for assignment in the selected workspace."),
					stayOpen: false,
					status: "error"
				});
				OTDCPWAssignToProductView.getcount(view, selectedWorkspaceLength, contentArea, false);
				return;
			}

			let selectionContext = {
				"selection_context_param": {
					"selection_context": {
						"asset_ids": assetIdList,
						"asssetContentType": [],
						"assetSubContentType": [],
						"type": "com.artesia.asset.selection.AssetIdsSelectionContext",
						"include_descendants": "NONE"
					}
				}
			};
			let params = {
				"type": "add"
			};
			params.selection_context = JSON.stringify(selectionContext);
			let serviceUrl = "/folders/" + subFolderId + "/children";
			otui.put(
				otui.service + serviceUrl,
				params,
				otui.contentTypes.formData,
				function (response, status, success) {
					//Upon response, track count of successes failures
					OTDCPWAssignToProductView.getcount(view, selectedWorkspaceLength, contentArea, success);
				}
			);
		}

		function findSubFolderId(response) {
			let subFolderId;
			let isFirstFolder = true;
			let subFolderName =
				OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName
					? OTDCPWWorkspaceManager.subFolder_name
					: OTDCBWWorkspaceManager.subFolder_name;

			for (const entry of response) {
				if (entry.container_id) {
					if (isFirstFolder) {
						subFolderId = entry.asset_id;
						isFirstFolder = false;
					}
					if (entry.name === subFolderName) {
						subFolderId = entry.asset_id;
						break;
					}
				}
			}
			return subFolderId;
		}
	}

	OTDCPWAssignToProductView.getcount = function getcount(view, SelectedWorkspaceLength, contentArea, success) {
        view.internalProperties.totalCount += 1;
        if (success) {
            view.internalProperties.successCount += 1;
        }
        if (view.internalProperties.totalCount == SelectedWorkspaceLength) {
            if (view.internalProperties.successCount == SelectedWorkspaceLength) {
                let msg = otui.tr( OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName ? 'Successfully assigned asset(s) to the selected product(s)' :
					'Successfully assigned asset(s) to the selected EAM workspace(s)');
                let notification = {
                    'message': msg,
                    'status': 'ok'
                };
                otui.NotificationManager.showNotification(notification);
            } else if (view.internalProperties.successCount == 0) {
				let statusMsg =  OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName ? otui.tr("Assign to product failed for the selected product(s)") :
otui.tr("Assign to EAM workspace failed for the selected EAM workspace(s)");
				let msg = statusMsg;
                otui.NotificationManager.showNotification({
                    message: msg,
                    stayOpen: false,
                    status: "warning"
                });				
			}else {
				let statusMsg =  OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName ? otui.tr("Assign to product failed for some workspaces") :
otui.tr("Assign to EAM workspace failed for some workspaces");
                let msg = statusMsg;
                otui.NotificationManager.showNotification({
                    message: msg,
                    stayOpen: false,
                    status: "warning"
                });
            }
			//Unblock view
			view.unblockContent();
			otui.DialogUtils.cancelDialog(contentArea, true);
        }
    };

	let _load = function _load(location){
		let self = this;
		self.blockContent();
		if( OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName){
			$("#article_id").show();
			$("#workspace_title").text(otui.tr('Product Workspaces'));
			$("#workspace_title").attr('ot-token', 'Product Workspaces');
			let modalTitles = $('.ot-modal-dialog-title');
			modalTitles.text(otui.tr('Assign to Product'));
		} else {
			$("#article_id").hide();
			$("#workspace_title").text(otui.tr('EAM Workspaces'));
			$("#workspace_title").attr('ot-token', 'EAM Workspaces');
			let modalTitles = $('.ot-modal-dialog-title');
			modalTitles.text(otui.tr('Assign to EAM Workspace'));
		}
		_get(self, function(response)
		{
			otui.empty(self.rowLocation().get(0));
			//while mapping the response to row template, add description and article details
			let results = $($.map(response, function(entry)
				{ 	
					let metadata = entry.metadata && entry.metadata.metadata_element_list && entry.metadata.metadata_element_list || [];	
					for (const meta of metadata) {
						if (meta.id == "OTDC.PW.DESCRIPTION" || meta.id == "OTDCBW.BO.DESC") {
							entry.description = meta.value.value && meta.value.value.value;
						}
						if (OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName && meta.id == "OTDC.PW.ARTICLE.NUMBER") {
							entry.article_id = meta.value.value && meta.value.value.value;
						}
					}				
					return self.addRowFromTemplate(entry); 
				}));
			let rowLocation = self.rowLocation();
			rowLocation.append(results);
			let ContentArea = self.contentArea();
			for (const asset of response)
			{
				//Set thumbnail
				if(!asset.thumbnail_content_id){
					continue;
				}					
				let rowObject = ContentArea.find('ot-resource[resourceid="' + asset.asset_id + '"]');
				let thumbnailWrapper = rowObject.find(".ot-workflowjob-thumbnails");
				let thumbnailUrl = otui.service + "/assets/" + asset.asset_id + "/contents?rendition_type=thumbnail";
				let thumbnailEntry = self.getTemplate("thumbnailEntry");
				thumbnailEntry.querySelector(".ot-workflowjob-thumbnail-entry").setAttribute("src", thumbnailUrl);
				thumbnailWrapper[0].appendChild(thumbnailEntry);
			}

			//Set visual if any asset is selected previously, navigated to other page and now navigated back to the same page
			SelectionManager.updateSelectionVisual(self);
			PageManager.updatePaging(self, self.properties.pageID);
			self.unblockContent();
		});
	}

	let _get = function _get(self,callback){
		let service = self.storedProperty("service");
		let data = {};
		if( OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName){
			data.nodeID = OTDCPWWorkspaceManager.root_folder;
		} else {
			data.nodeID = OTDCBWWorkspaceManager.root_folder;
		}
		data.pageID = self.properties.pageID;//This is for pageID which will be used for pagination
		data.pageProperties = self.properties.pageProperties;
		data.appliedFacets = self.properties.appliedFacets;

		//Set isMosaic to false to fetch metadata of workspace as well(method OTDCUtilities.doFolderChildrenRead), so as to display it in table
		self.properties.isMosaic = false;
		otui.services[service].read(data, callback, self);
	}

    //Wrapper View
    exports.OTDCPWAssignToProdWrapper = exports.OTDCPWAssignToProdWrapper = otui.define("OTDCPWAssignToProdWrapper" ,["withChildren"], function(){
		this.properties = {
			'name' : 'OTDCPWAssignToProdWrapper', // Name of the view
			'title': otui.tr("Assign to Product"),
			'type': 'OTDCPWAssignToProdWrapper'
		}

		this._initContent = function _initContent(self, callback) {
			self.properties.title=otui.tr( OTDCPWAssignToProductView.dialogProperties.context === OTDCUtilities.PWContextName? "Assign to Product": "Assign to EAM Workspace");
			let template = self.getTemplate("content");				
			callback(template);
            self.addChildView(new OTDCPWSidebarView());
            self.addChildView(new OTDCPWAssignToProductView());
		}
	});

    OTDCPWAssignToProdWrapper.show = function (data, options) {
		options = options || {};
        data = data || {};
        OTDCPWAssignToProductView.dialogProperties = data;	
		OTDCPWAssignToProdWrapper.dialogProperties = data;	
        otui.dialog("otdcpwassigntoprodwrapperdialog", options.viewProperties);
    };
	

})(window);
