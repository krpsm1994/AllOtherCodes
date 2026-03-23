(function (exports) {
	let OTDCPWAssetActionsManager = exports.OTDCPWAssetActionsManager = function OTDCPWAssetActionsManager() { };

	//Adds Mark for Assignment menu item to asset actions in folders view and Product Dashboard
	OTDCPWAssetActionsManager.setupMarkForAssignment = function (event, resource, point) {
		let enable = false;

		if (!resource
			|| otui.is(otui.modes.PHONE)
			|| otui.resourceAccessors.type(resource) == 'folder'
			|| resource.deleted == true
			|| OTDCPWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDC.PW.ASSET.MARKFORASSIGNMENT")
		) {
			return enable;
		}
		//Get current view
		let view = AssetActions.getCurrentView(event);
		if (view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView) {
			if (OTDCUtilities.isPWContext() && OTDCPWAssetActionsManager.getProductIdFromWorkspace(view)) {
				enable = true;
			}
		} else if (view instanceof SearchView || view instanceof FolderResultsView) {
			enable = true;
		}

		return enable;
	};

	//Adds Mark for Assignment menu action in asset headers for Folders view and Product Dashboard
	OTDCPWAssetActionsManager.setupMarkForAssignmentForHeader = function (event, resource, point) {
		let enable = false;
		if (otui.is(otui.modes.PHONE)
			|| OTDCPWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDC.PW.ASSET.MARKFORASSIGNMENT")
		) {
			return enable;
		}

		let view = AssetActions.getCurrentView();

		if (view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView) {
			//Check for Product ID in workspace. if not available, return
			if (!OTDCPWAssetActionsManager.getProductIdFromWorkspace(view)) {
				return enable;
			}
		} else if (!(view instanceof SearchView) && !(view instanceof FolderResultsView)) {
			//If view is not SearchView or FolderResultsView, return.
			return enable;
		}

		let selection = SelectionManager.selections.get(view);
		let currentPageResourcesList = view.internalProperties.resultData;

		if (!view.internalProperties.otdcSelectedResourcesCache) {
			view.internalProperties.otdcSelectedResourcesCache = new Map();
		}

		let selectedResourcesCache = view.internalProperties.otdcSelectedResourcesCache;

		//Atleast one asset must be selected and we are not supporting selecting all assets from all pages at a time.
		if (selection.assetList && selection.assetList.length > 0
			&& selection.selectionState != SelectionManager.selectionStates.ALL_PAGES) {
			let tempMap = new Map();
			for (let index = 0; index < selection.assetList.length; index++) {
				tempMap.set(selection.assetList[index].asset_id, true);
			}
			for (let res in currentPageResourcesList) {
				if (tempMap.has(res.asset_id)) {
					selectedResourcesCache.set(res.asset_id, res);
				}
			}
			enable = true;
		} else if (selection.assetList && selection.assetList.length == 0) {
			selectedResourcesCache.clear();
		}

		return enable;
	};

	//Adds Generate AI Tags menu item to asset actions in Product Dashboard
	OTDCPWAssetActionsManager.setupGenerateAITags = function (event, resource, point) {
		let enable = false;

		if (!resource
			|| otui.is(otui.modes.PHONE)
			|| otui.resourceAccessors.type(resource) == 'folder'
			|| resource.deleted == true
			|| OTDCPWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDCAI.ASSET.GENERATEAITAGS")
			|| !OTDCUtilities.isPWContext()
		) {
			return enable;
		}

		//Get current URL parameters
		const urlParams = new URLSearchParams(window.location.search);
		const paramP = urlParams.get('p');

		//Get current view
		let view = AssetActions.getCurrentView(event);
		if (view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView) {
			enable = paramP.startsWith('otdc');
		}

		return enable;
	};

	//Adds Generate AI tags menu action in asset headers for Product Dashboard assets.
	OTDCPWAssetActionsManager.setupGenerateAITagsForHeader = function (event, resource, point) {
		let enable = false;
		if (otui.is(otui.modes.PHONE)
			|| OTDCPWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDCAI.ASSET.GENERATEAITAGS")
			|| !OTDCUtilities.isPWContext()
		) {
			return enable;
		}

		//Get current URL parameters
		const urlParams = new URLSearchParams(window.location.search);
		const paramP = urlParams.get('p');

		//Get current view
		let view = AssetActions.getCurrentView();
		//Get current selection
		let selection = SelectionManager.selections.get(view);
		//Atleast one asset must be selected and we are not supporting multiple asset selection.
		if ((view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView) && selection.assetList && selection.assetList.length === 1) {
			enable = paramP.startsWith('otdc');
		}

		return enable;
	};

	//Adds Assign to product menu item to asset actions in folders view and Product Dashboard
	OTDCPWAssetActionsManager.setupAssignToProduct = function (event, resource, point) {
		let enable = false;

		if (!resource
			|| otui.is(otui.modes.PHONE)
			|| otui.resourceAccessors.type(resource) == 'folder'
			|| resource.deleted == true
			|| OTDCPWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDC.PW.ASSET.ASSIGNTOPRODUCT")
		) {
			return enable;
		}

		//Get current URL parameters
		const urlParams = new URLSearchParams(window.location.search);
		const paramP = urlParams.get('p');

		//Get current view
		let view = AssetActions.getCurrentView();
		if (view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView) {
			enable = paramP.startsWith('otdc') && OTDCUtilities.isPWContext();
		} else if (view instanceof SearchView || view instanceof FolderResultsView) {
			enable = true;
		}
		return enable;
	};

	//Adds Assign To Product menu action in asset headers for Folders view and Product Dashboard
	OTDCPWAssetActionsManager.setupAssignToProductForHeader = function (event, resource, point) {
		let enable = false;
		//Get current URL parameters
		const urlParams = new URLSearchParams(window.location.search);
		const paramP = urlParams.get('p');

		if (otui.is(otui.modes.PHONE)
			|| OTDCPWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDC.PW.ASSET.ASSIGNTOPRODUCT")
			|| (paramP.startsWith('otdc') && OTDCUtilities.isEAMContext())
		) {
			return enable;
		}
		const supportedViews = [OTDCPWAssetsView, OTDCPWAssetsEditView, FolderResultsView, SearchView];
		//Get current view
		let view = AssetActions.getCurrentView(event);
		let selection = SelectionManager.selections.get(view);
		if(supportedViews.some(supportedView => view instanceof supportedView)
			&& selection.assetList && selection.assetList.length > 0 //check if atleast 1 asset is selected
			&& selection.selectionState != SelectionManager.selectionStates.ALL_PAGES){
				//TODO: Verify ALL Pages scenario limit to certain number 
				enable = true;
		}

		return enable;
	};

	//Adds Assign to EAM Workspace menu item to asset actions in EAM Dashboard
	OTDCPWAssetActionsManager.setupAssignToWorkspace = function (event, resource, point) {
		let enable = false;

		if (!resource
			|| otui.is(otui.modes.PHONE)
			|| otui.resourceAccessors.type(resource) == 'folder'
			|| resource.deleted == true
			|| OTDCBWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDCBW.ASSET.ASSIGNTOWORKSPACE")
		) {
			return enable;
		}

		//Get current URL parameters
		const urlParams = new URLSearchParams(window.location.search);
		const paramP = urlParams.get('p');

		//Get current view
		let view = AssetActions.getCurrentView(event);
		if (view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView) {
			enable = paramP.startsWith('otdc') && OTDCUtilities.isEAMContext();
		} else if (view instanceof SearchView || view instanceof FolderResultsView) {
			enable = true;
		}

		return enable;
	};

	//Adds Assign To Workspace menu action in asset headers for Folders view and EAM Dashboard
	OTDCPWAssetActionsManager.setupAssignToWorkspaceForHeader = function (event, resource, point) {
		let enable = false;
		//Get current URL parameters
		const urlParams = new URLSearchParams(window.location.search);
		const paramP = urlParams.get('p');

		if (otui.is(otui.modes.PHONE)
			|| OTDCBWWorkspaceManager.isInitialized != "true"
			|| !otui.UserFETManager.isTokenAvailable("OTDCBW.ASSET.ASSIGNTOWORKSPACE")
			|| (paramP.startsWith('otdc') && !OTDCUtilities.isEAMContext())
		) {
			return enable;
		}
		const supportedViews = [OTDCPWAssetsView, OTDCPWAssetsEditView, FolderResultsView, SearchView];
		//Get current view
		let view = AssetActions.getCurrentView();
		let selection = SelectionManager.selections.get(view);
		if(supportedViews.some(supportedView => view instanceof supportedView)
			&& selection.assetList && selection.assetList.length > 0 //check if atleast 1 asset is selected
			&& selection.selectionState != SelectionManager.selectionStates.ALL_PAGES){
				enable = true;
		}

		return enable;
	};

	//Adds Set as Workspace thumbnail menu action in asset actions for Product Dashboard and EAM Dashboard.
	OTDCPWAssetActionsManager.setupWorkspaceThumbnail = function (event, resource, point) {
		let enable = false;

		//Get current URL parameters
		const urlParams = new URLSearchParams(window.location.search);
		const paramP = urlParams.get('p');

		if(!resource
			|| otui.is(otui.modes.PHONE)
			|| (OTDCPWWorkspaceManager.isInitialized != "true" && OTDCBWWorkspaceManager.isInitialized != "true")
			|| !paramP.startsWith('otdc')
		){
			return enable;
		}

		//Get current view
		let view = AssetActions.getCurrentView();
		if(view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView){
			enable = true;
		}

		return enable;
	};

	//Click actions for all menu items start here

	OTDCPWAssetActionsManager.assignToProductFromHeader = function (event, resource, point) {
		let view = AssetActions.getCurrentView();
		let selection = SelectionManager.selections.get(view);
		let selectedAssets = [];
		for (let i = 0; i < selection.assetList.length; i++) {
			selectedAssets.push(selection.assetList[i].asset_id);
		}
		let data = {};
		data.srcView = view;
		data.selectedAssets = selectedAssets;
		if(event.target.parentElement.title === 'Assign to EAM Workspace'){
			data.context = OTDCUtilities.BWContextName;
		} else {
			data.context = OTDCUtilities.PWContextName;
		}
		exports.OTDCPWAssignToProdWrapper.show(data, selectedAssets);
	};

	OTDCPWAssetActionsManager.generateAITagsFromHeader = function (event, resource, point) {
		let view = AssetActions.getCurrentView();
		let selection = SelectionManager.selections.get(view);
		let selectedAssets = [];
		for (let i = 0; i < selection.assetList.length; i++) {
			selectedAssets.push(selection.assetList[i]);
		}
		let data = {};
		data.srcView = view;
		data.selectedAssets = selectedAssets;
		OTDCPWAssetActionsManager.analyseTheAsset(selectedAssets[0], view, undefined, true);
	};

	OTDCPWAssetActionsManager.markForAssignmentFromHeader = function (event, resource, point) {
		let view = AssetActions.getCurrentView();
		let selection = SelectionManager.selections.get(view);
		//Get selected assets
		let assetList = selection.assetList;
		//Get selected resources cache
		let selectedResourcesCache = view.internalProperties.otdcSelectedResourcesCache;
		//Prepare resource array with selected assets
		let resourceList = [];
		for (let index = 0; index < assetList.length; index++) {
			let asset = selectedResourcesCache.get(assetList[index].asset_id);
			if (asset) {
				resourceList.push(asset);
			}
		}

		OTDCPWAssetActionsManager.openMarkForAssignmentDialog(view, resourceList);
	};

	OTDCPWAssetActionsManager.assignToProduct = function (event, resource, point) {
		//resource variable contains the selected asset details
		let view = AssetActions.getCurrentView();
		let selectedAssets = resource.asset_id;
		let data = {};
		data.srcView = view;
		data.selectedAssets = selectedAssets;
		if(this.name === "assignToWorkspace"){
			data.context = OTDCUtilities.BWContextName;
		} else {
			data.context = OTDCUtilities.PWContextName;
		}
		exports.OTDCPWAssignToProdWrapper.show(data, selectedAssets);
	};

	OTDCPWAssetActionsManager.generateAITags = function (event, resource, point) {
		//resource variable contains the selected asset details
		let view = AssetActions.getCurrentView();
		OTDCPWAssetActionsManager.analyseTheAsset(resource, view, undefined, false);
	};


	OTDCPWAssetActionsManager.openMarkForAssignmentDialog = function (view, resourceList) {
		let isWorkspaceView = false;
		if (view instanceof OTDCPWAssetsView || view instanceof OTDCPWAssetsEditView) {
			isWorkspaceView = true;
		}

		if(resourceList.length === 0) {
			let selection = SelectionManager.selections.get(view);
			for (let i = 0; i < selection.assetList.length; i++) {
				resourceList.push(selection.assetList[i]);
			}
		}

		let productId, variantList;
		if (isWorkspaceView) {
			//Get product code from workspace.
			try {
				let wsResource = view.internalProperties.parentView.internalProperties.parentView.childViews[1].properties.assetInfo;
				productId = OTDCPWAssetActionsManager.getMetadataFieldValue(wsResource, 'OTDC.PW.ARTICLE.NUMBER', 'OTDC.PW.FG.INFO');
				// Read variant list from product workspace
				let variantValues = OTDCPWAssetActionsManager.getMetadataTableFieldValues(wsResource, 'OTDC.PW.TF.VARIANTCODE', 'OTDC.PW.VARIANTS','OTDC.PW.FG.VARIANTS');
				// If data is available, assign it to mark for assign dialog
				if(variantValues && variantValues.length>0){
					variantList = OTDCPWAssetActionsManager.convertTableFldValuesToSimpleArray(variantValues);
				}
			} catch (error) {
				let erroMsg = otui.tr("Cannot perform mark for assignment: Unable to read Article number from workspace");
				OTDCPWAssetActionsManager.showError(erroMsg, view);
				return;
			}

			if (!productId) {
				let error = otui.tr("Cannot perform mark for assignment: Article number is not set in workspace");
				OTDCPWAssetActionsManager.showError(error, view);
				return;
			}
		}

		//Validate whether selected assets have required metadata fields.
		let codeFldCriteria = OTDCPWAssetActionsManager.parseMetadataFldCriteria(
			OTDCPWWorkspaceManager.config.markForAssign.codeFldCriteria);
		let sysIdFldCriteria = OTDCPWAssetActionsManager.parseMetadataFldCriteria(
			OTDCPWWorkspaceManager.config.markForAssign.sysIdFldCriteria);
		let attrIdFldCriteria = OTDCPWAssetActionsManager.parseMetadataFldCriteria(
			OTDCPWWorkspaceManager.config.markForAssign.attrIdFldCriteria);

		let unfitResources = [];
		let filteredResources = [];
		//If model is already verified skip re-verification.
		let checkedModels = new Map();
		//Capture models which doesn't have required metadata fields.
		let failedModels = new Map();
		//Capture names of assets which doesn't have required metadata fields.
		for (let index = 0; index < resourceList.length; index++) {
			let asset = resourceList[index];
			let modelId = asset.metadata_model_id;
			let isValidResource = true;
			if (otui.resourceAccessors.type(asset) == 'folder'
				|| asset.deleted == true) {
				continue;
			}

			if (checkedModels.has(modelId)) {
				//If model is already verified skip verification.
				if (failedModels.has(modelId)) {
					unfitResources.push(asset.name);
					isValidResource = false;
				}
			} else {
				//Peform validation.
				checkedModels.set(modelId, true);
				let metadata = otui.MetadataModelManager.getModelByName(modelId);
				if (!OTDCPWAssetActionsManager.isMetadataFieldExists(metadata, codeFldCriteria)
					|| !OTDCPWAssetActionsManager.isMetadataFieldExists(metadata, sysIdFldCriteria)
					|| !OTDCPWAssetActionsManager.isMetadataFieldExists(metadata, attrIdFldCriteria)) {
					unfitResources.push(asset.name);
					isValidResource = false;
					failedModels.set(modelId, true);
				}
			}
			if (isValidResource) {
				filteredResources.push(asset);
			}
		}

		//Show an error with all asset names which doesn't have required metadata fields.
		if (filteredResources.length == 0) {
			let error = otui.tr("Please select only active asset(s) with required metadata.");
			OTDCPWAssetActionsManager.showError(error, view);
			return;
		}

		//Open mark for assignment dialog
		let data = {
			resourceList: filteredResources,
			showSelectProductsArea: !isWorkspaceView,
			productId: productId,
			selectionHasUnfitResources: unfitResources.length > 0 ? true : false
		};
		if(variantList){
			data.variantList = variantList;
		}
		OTDCPWMarkForAssignmentView.show(data);
	};

	/**
	 * Performs mark for assignment
	 * @param {*} view 
	 * @param {*} assignmentMap 
	 * @param {*} productList List of selected products, it will always be single product if it is initiated from PW
	 * @returns 
	 */
	OTDCPWAssetActionsManager.performMarkForAssignment = function (view, assignmentMap, productList) {
		if (!assignmentMap || assignmentMap.size == 0
			|| !productList || productList.length == 0) {
			return; //TODO show some error
		}
		let successfulRequests = 0;
		let failedRequests = 0;
		let systemId = OTDCPWWorkspaceManager.config.pim.systemId;

		assignmentMap.forEach(function (obj, key) {
			let assetList = obj.assetList || [];
			let pimMedia = obj.pimMedia || [];

			let productIDVals = [];
			let systemIDVals = [];
			let mediaAttrVals = [];
			let isDefaultMediaAttr = false;
			if (key.startsWith('OTDCPW_ASSET_DEFAULT_MEDIA_ATTR') || !pimMedia || pimMedia.length == 0) {
				isDefaultMediaAttr = true;
			}

			// Check if variant list is appended to key
			let variantList = OTDCPWAssetActionsManager.extractProductsListFromKey(key);
			var actualProdList;
			if(variantList && variantList.length > 0){
				// Use variants list if available
				actualProdList = variantList;
			} else{
				actualProdList = productList;
			}

			for (let index = 0; index < actualProdList.length; index++) {
				if (isDefaultMediaAttr) {
					productIDVals.push({
						"value": {
							"type": "string",
							"value": actualProdList[index]
						}
					});
					systemIDVals.push({
						"value": {
							"type": "string",
							"value": systemId
						}
					});
				} else {
					for (let mediaAttrIndex = 0; mediaAttrIndex < pimMedia.length; mediaAttrIndex++) {
						productIDVals.push({
							"value": {
								"type": "string",
								"value": actualProdList[index]
							}
						});
						systemIDVals.push({
							"value": {
								"type": "string",
								"value": systemId
							}
						});
						mediaAttrVals.push({
							"value": {
								"type": "string",
								"value": pimMedia[mediaAttrIndex]
							}
						});
					}
				}
			}

			let codeFldCriteria = OTDCPWAssetActionsManager.parseMetadataFldCriteria(
				OTDCPWWorkspaceManager.config.markForAssign.codeFldCriteria);
			let sysIdFldCriteria = OTDCPWAssetActionsManager.parseMetadataFldCriteria(
				OTDCPWWorkspaceManager.config.markForAssign.sysIdFldCriteria);
			let attrIdFldCriteria = OTDCPWAssetActionsManager.parseMetadataFldCriteria(
				OTDCPWWorkspaceManager.config.markForAssign.attrIdFldCriteria);
			let fieldValueEntries = [];
			fieldValueEntries.push({
				"metadata_field_id": codeFldCriteria.id,
				"metadata_value": productIDVals
			});
			if (systemId) {
				fieldValueEntries.push({
					"metadata_field_id": sysIdFldCriteria.id,
					"metadata_value": systemIDVals
				});
			}
			if (mediaAttrVals.length > 0) {
				fieldValueEntries.push({
					"metadata_field_id": attrIdFldCriteria.id,
					"metadata_value": mediaAttrVals
				});
			}

			let bulkEditParams = {
				"bulk_edit_request_param": {
					"bulk_edit_request": {
						"context": {
							"asset_ids": assetList,
							"type": "com.artesia.asset.selection.AssetIdsSelectionContext"
						},
						"metadata_values": {
							"metadata_edit_id_value_list": [
								{
									"field_id_value_entries": fieldValueEntries,
									"metadata_section_id": "METADATA_APPEND_SECTION"
								}
							]
						}
					}
				}
			};

			//Perform bulk edit
			otui.patch(otui.service + "/assets", JSON.stringify(bulkEditParams), otui.contentTypes.json, function (response, status, success) {
				if (success) {
					successfulRequests++;
				} else {
					failedRequests++;
				}

				if (successfulRequests + failedRequests == assignmentMap.size) {
					if (failedRequests == 0) {
						otui.NotificationManager.showNotification({
							'message': otui.tr('Mark for assignment initiated successfully.'),
							'status': 'ok'
						});
					} else if (successfulRequests > 0) {
						otui.NotificationManager.showNotification({
							'message': otui.tr('Cannot perform mark for assignment for some assets.'),
							'status': 'ok'
						});
					} else {
						otui.NotificationManager.showNotification({
							message: otui.tr("Cannot perform mark for assignment."),
							stayOpen: false,
							status: "warning"
						});
					}
					view.unblockContent();
					otui.DialogUtils.cancelDialog(view.contentArea(), true);
				}
			});
		});

	};

	OTDCPWAssetActionsManager.extractProductsListFromKey = function (key) {
		const index = key.indexOf("|");
    	if (index === -1 || index === key.length - 1) return [];
	
		return key.substring(index + 1).split(",");
	};

	/**
	 * Mark for assignment handler for asset level actions.
	 * @param {*} event 
	 * @param {*} resource 
	 * @param {*} point 
	 * @returns 
	 */
	OTDCPWAssetActionsManager.markForAssignment = function (event, resource, point) {
		let view = AssetActions.getCurrentView();

		let assetID = resource?.asset_id;
		if (!assetID) {
			return;
		}
		let resourceList = [];
		resourceList.push(resource);

		OTDCPWAssetActionsManager.openMarkForAssignmentDialog(view, resourceList);
	};

	/**
	 * Checks whether given field exists within the metadata model or not
	 * @param {*} metadata it could be resource.metadata OR model.metadata
	 * @param {*} fldCriteria 
	 * fldCriteria requires following fields
	 * {
	 * 	 id: <field_id> - mandatory
	 * 	 grpId: <field group id> - optional - Only looks into specific field group if this set
	 *   tabular: <boolean> - optional - By default it only checks within scalar. If this flag is set it will check in tabular metadata.
	 *   tableId: <table_id> - optional - Only looks into specific table if this set
	 * }
	 * @returns boolean
	 */
	OTDCPWAssetActionsManager.isMetadataFieldExists = function (metadata, fldCriteria) {
		if (!metadata?.metadata_element_list || !fldCriteria.id) {
			return false;
		}

		for (let fldGrpIndex = 0; fldGrpIndex < metadata.metadata_element_list.length; fldGrpIndex++) {
			//Check in each field group
			let fldGrp = metadata.metadata_element_list[fldGrpIndex];
			if (fldCriteria.grpId && fldCriteria.grpId != fldGrp.id) {
				//Skip field group if field group doesn't match.
				continue;
			}
			for (let fldIndex = 0; fldIndex < fldGrp.metadata_element_list.length; fldIndex++) {
				//Check in each field
				let fld = fldGrp.metadata_element_list[fldIndex];
				if (fldCriteria.isTabular && fld.type == 'com.artesia.metadata.MetadataTable') {
					if (fldCriteria.tableId && fldCriteria.tableId != fld.id) {
						//Skip table if table doesn't match.
						continue;
					}
					for (let tabFldIndex = 0; tabFldIndex < fld.metadata_element_list.length; tabFldIndex++) {
						if (fld.metadata_element_list[tabFldIndex].id == fldCriteria.id) {
							return true;
						}
					}
				} else if (fld.type == 'com.artesia.metadata.MetadataField'
					&& fld.id == fldCriteria.id) {
					//Search only in scalar fields if it's scalar field
					return true;
				}
			}
		}
		return false;
	};

	/**
	 * Parses field criteria
	 * @param {*} fldCriteria 
	 * @returns parsed field criteria
	 */
	OTDCPWAssetActionsManager.parseMetadataFldCriteria = function (fldCriteria) {
		if (!fldCriteria) {
			return {};
		}

		let result = {};

		let bracketStart = fldCriteria.indexOf('[');
		if (bracketStart != -1) {
			let bracketEnd = fldCriteria.indexOf(']');
			if (bracketEnd > bracketStart) {
				result.grpId = fldCriteria.substring(bracketStart + 1, bracketEnd).trim();
				fldCriteria = fldCriteria.substring(0, bracketStart)
			}
		}

		let slashStart = fldCriteria.indexOf('/');
		if (slashStart != -1) {
			let args = fldCriteria.split('/');
			result.isTabular = true;
			result.tableId = args[0].trim();
			result.id = args[1].trim();
		} else {
			result.id = fldCriteria;
		}
		return result;
	};

	/**
	 * Gets metadata field value for field of type MetadataField.
	 * @param {*} resource 
	 * @param {*} fldId 
	 * @param {*} targetFldGrp 
	 * @returns 
	 */
	OTDCPWAssetActionsManager.getMetadataFieldValue = function (resource, fldId, targetFldGrp) {
		if (!resource?.metadata || !resource.metadata.metadata_element_list
			|| !fldId) {
			return null;
		}

		for (let fldGrpIndex = 0; fldGrpIndex < resource.metadata.metadata_element_list.length; fldGrpIndex++) {
			//Check in each field group
			let fldGrp = resource.metadata.metadata_element_list[fldGrpIndex];
			if (targetFldGrp && targetFldGrp != fldGrp.id) {
				continue;
			}

			for (let fldIndex = 0; fldIndex < fldGrp.metadata_element_list.length; fldIndex++) {
				//Check in each field
				let fld = fldGrp.metadata_element_list[fldIndex];
				if (fld.id == fldId) {
					if (fld.type == 'com.artesia.metadata.MetadataField') {
						try {
							if (fld.value.domain_value) {
								return fld.value.value.field_value.value;
							} else {
								return fld.value.value.value;
							}
						} catch (error) {
							return undefined;
						}
					}
					return null;
				}
			}
		}

		return null;
	};

	/**
	 * Retrieves table field values for given table field information.
	 * @param {*} resource Asset/Folder resource object
	 * @param {*} tblFldId Table field ID
	 * @param {*} tableId  Table ID
	 * @param {*} targetFldGrp Field Group which this table is part of (optional)
	 * @returns Values Array
	 */
	OTDCPWAssetActionsManager.getMetadataTableFieldValues = function (resource, tblFldId, tableId, targetFldGrp) {
		// Ensure resource and required fields are present
		if (!resource?.metadata?.metadata_element_list || !tblFldId || !tableId) {
			return null;
		}
	
		for (const fldGrp of resource.metadata.metadata_element_list) {
			if (targetFldGrp && targetFldGrp !== fldGrp?.id) continue;
	
			// Ensure metadata_element_list exists before iterating
			if (!Array.isArray(fldGrp?.metadata_element_list)) continue;
	
			for (const fld of fldGrp.metadata_element_list) {
				if (fld?.type === 'com.artesia.metadata.MetadataTable' && fld?.id === tableId) {
					
					// Ensure metadata_element_list exists before searching
					if (!Array.isArray(fld?.metadata_element_list)) continue;
	
					const tblFld = fld.metadata_element_list.find(
						el => el?.id === tblFldId
					);
	
					if (tblFld?.values) return tblFld.values;
				}
			}
		}

		return null;
	};

	/**
	 * Sorts and formats the table field values into comma separted string.
	 * @param {*} values Table field values array
	 * @returns sorted and formatted string
	 */
	OTDCPWAssetActionsManager.sortAndFormatTableFldValues = function (values) {
		// Return empty string if values is not an array
		if (!Array.isArray(values)) return "";
		
		const extractedValues = [];
		// Extract values and push them into the array
		for (const val of values) {
			if (val?.value?.value) {
				extractedValues.push(val.value.value);
			}
		}

		// Sort the extracted values alphabetically
		extractedValues.sort();

		// Convert sorted array into a comma-separated string
		return extractedValues.join(",");
	};

	/**
	 * Converts table field values into a simpler array format
	 * @param {*} values table field values array
	 * @returns Array
	 */
	OTDCPWAssetActionsManager.convertTableFldValuesToSimpleArray = function (values) {
		// Return empty string if values is not an array
		if (!Array.isArray(values)) return [];
		
		const extractedValues = [];
		// Extract values and push them into the array
		for (const val of values) {
			if (val?.value?.value) {
				extractedValues.push(val.value.value);
			}
		}

		return extractedValues;
	};

	/**
	 * Validates given data with master data and returns true or false. Assuming both are arrays.
	 * 
	 * @param {*} data source array which you want to validate
	 * @param {*} masterData Master data against which we validate
	 * @returns boolean
	 */
	OTDCPWAssetActionsManager.validateArrayDataWithMaster = function (data, masterData) {
		if (!Array.isArray(data) || !Array.isArray(masterData)) {
			return false; // Ensure both inputs are arrays
		}
	
		return data.every(val => masterData.includes(val));
	};

	/**
	 * Unblocks view if view is pased and display's an error.
	 * @param {*} error 
	 * @param {*} view 
	 */
	OTDCPWAssetActionsManager.showError = function (error, view) {
		if (view) {
			view.unblockContent();
		}

		otui.NotificationManager.showNotification({
			'message': error,
			'status': 'error',
			'stayOpen': true
		});
	};

	OTDCPWAssetActionsManager.setAsWorkspaceThumbnail = function (event, resource, point) {
		let view = AssetActions.getCurrentView();
		if( !view){
			let uploadAssetElement = document.getElementsByClassName('ot-collection-list-action ot-as-list');
			view = otui.Views.containing(uploadAssetElement[0]);
		}
		//resource variable contains the selected asset details
		event.stopPropagation();
		SelectionManager.singleAssetSelection = resource;
		//Get the workspace ID and name and pass to thumbnail editor
		let parentView = view.internalProperties.parentView;
		let folderID = parentView.internalProperties.parentView.internalProperties.asset.asset_id;
		let folderName = parentView.internalProperties.parentView.internalProperties.asset.name;
		let options = {
			'minWidth': 600,
			'minHeight': 660,
			'viewProperties': { "FolderThumbnailEditorView": { "assetName": resource.name, 'folderID': folderID, 'folderName': folderName, 'canRender': false } }
		};
		otui.dialog("folderthumbnaileditordialog", options);
	};

	OTDCPWAssetActionsManager.getProductIdFromWorkspace = function (assetsView) {
		try {
			let wsResource = assetsView.internalProperties.parentView.internalProperties.parentView.childViews[1].properties.assetInfo;
			return OTDCPWAssetActionsManager.getMetadataFieldValue(wsResource, 'OTDC.PW.ARTICLE.NUMBER', 'OTDC.PW.FG.INFO')
		} catch (error) {
			return null;
		}
	};

	OTDCPWAssetActionsManager.analyseTheAsset = function (asset, view, callback, fromheader) {
		let taggingType = '';
		let groceryCategory = '';

		if (!fromheader) {
			//Determine the tagging type (Fashion/Grocery) anf Grocery Category
			asset.metadata?.metadata_element_list.forEach(fieldGroup => {
				if (fieldGroup.id === 'OTDCAI.FG.GROCERY.TAGS') {
					taggingType = 'GROCERY';
					fieldGroup.metadata_element_list.forEach(field => {
						if (field.id === 'OTDCAI.GROCERY.CATEGORY') {
							groceryCategory = field.value.value;
						}
					});
				} else if (fieldGroup.id === 'OTDCAI.FG.FASHION.TAGS') {
					taggingType = 'FASHION';
				}
			});

			//Check Categoty in Inherited field if not available at asset level
			if (taggingType === 'GROCERY') {
				if (!groceryCategory) {
					if (asset.inherited_metadata_collections) {
						asset.inherited_metadata_collections.forEach(container => {
							container.inherited_metadata_values?.forEach(field => {
								if (field.id === 'OTDCAI.GROCERY.CATEGORY') {
									groceryCategory = field.metadata_element?.value?.value;
								}
							});
						});
					}
					if (!asset.inherited_metadata_collections || !groceryCategory) {
						otui.NotificationManager.showNotification({
							message: otui.tr("Asset doesn't have required metadata field : AI Product Category."),
							stayOpen: true,
							status: "error"
						});
						return;
					}
				}
			} else if (taggingType === '') {
				otui.NotificationManager.showNotification({
					message: otui.tr("Asset doesn't have required metadata."),
					stayOpen: true,
					status: "error"
				});
				return;
			}
		}

		view.blockContent();
		let session = jQuery.parseJSON(sessionStorage.session);
		$.ajax({
			type: "POST",
			url: "/ot-damlink/api/ai/tagAsset",
			data: JSON.stringify({
				auth: {
					loginName: session.login_name,
					messageDigest: session.message_digest,
					validationKey: session.validation_key,
					sessionId: session.id,
					userId: session.user_id
				},
				asset_id: asset.asset_id,
				input: {}
			}),
			contentType: 'application/json',
			success: function (json) {
				view.unblockContent();
				otui.NotificationManager.showNotification({
					message: otui.tr("Image tagged successfully."),
					stayOpen: false,
					status: "ok",
				});
				if (callback) {
					callback.call(view);
				}
			},
			error: function (error) {
				view.unblockContent();
				if (error?.responseText) {
					otui.NotificationManager.showNotification({
						message: otui.tr(error.responseText),
						stayOpen: true,
						status: "error"
					});
				} else {
					otui.NotificationManager.showNotification({
						message: otui.tr("Unable to analyze the image. Please check the logs."),
						stayOpen: true,
						status: "error"
					});
				}
			}
		});

	}

	otui.ready(function () {
		//Register actions at assets view header
		otui.GalleryViewActions.register({
			'name': 'setupVueAnalysisForHeader',
			'text': otui.tr('Generate AI Tags'),
			'img': 'ot_damlink_pw/style/img/AI_analysis.svg',
			'select': OTDCPWAssetActionsManager.generateAITagsFromHeader,
			'setup': OTDCPWAssetActionsManager.setupGenerateAITagsForHeader
		}, 0, 0);
		otui.GalleryViewActions.register({
			'name': 'assignToProductForHeader',
			'text': otui.tr('Assign to Product'),
			'img': 'ot_damlink_pw/style/img/assign_to_product.svg',
			'select': OTDCPWAssetActionsManager.assignToProductFromHeader,
			'setup': OTDCPWAssetActionsManager.setupAssignToProductForHeader
		}, 0, 0);
		otui.GalleryViewActions.register({
			'name': 'assignToWorkspaceForHeader',
			'text': otui.tr('Assign to EAM Workspace'),
			'img': 'ot_damlink_pw/style/img/assign_to_product.svg',
			'select': OTDCPWAssetActionsManager.assignToProductFromHeader,
			'setup': OTDCPWAssetActionsManager.setupAssignToWorkspaceForHeader
		}, 0, 0);
		otui.GalleryViewActions.register({
			'name': 'markForAssignmentForHeader',
			'text': otui.tr('Mark for assignment'),
			'img': 'ot_damlink_pw/style/img/mark_for_assignment.svg',
			'select': OTDCPWAssetActionsManager.markForAssignmentFromHeader,
			'setup': OTDCPWAssetActionsManager.setupMarkForAssignmentForHeader
		}, 0, 0);

		//Register actions at asset level
		otui.GalleryAssetActions.register({
			'name': 'setupVueAnalysis',
			'text': otui.tr('Generate AI Tags'),
			'img': 'ot_damlink_pw/style/img/AI_analysis.svg',
			'select': OTDCPWAssetActionsManager.generateAITags,
			'setup': OTDCPWAssetActionsManager.setupGenerateAITags
		}, 0, 0);
		otui.GalleryAssetActions.register({
			'name': 'assignToProduct',
			'text': otui.tr('Assign to Product'),
			'img': 'ot_damlink_pw/style/img/assign_to_product.svg',
			'select': OTDCPWAssetActionsManager.assignToProduct,
			'setup': OTDCPWAssetActionsManager.setupAssignToProduct
		}, 0, 0);
		otui.GalleryAssetActions.register({
			'name': 'assignToWorkspace',
			'text': otui.tr('Assign to EAM Workspace'),
			'img': 'ot_damlink_pw/style/img/assign_to_product.svg',
			'select': OTDCPWAssetActionsManager.assignToProduct,
			'setup': OTDCPWAssetActionsManager.setupAssignToWorkspace
		}, 0, 0);
		otui.GalleryAssetActions.register({
			'name': 'markForAssignment',
			'text': otui.tr('Mark for Assignment'),
			'img': 'ot_damlink_pw/style/img/mark_for_assignment.svg',
			'select': OTDCPWAssetActionsManager.markForAssignment,
			'setup': OTDCPWAssetActionsManager.setupMarkForAssignment
		}, 0, 0);
		otui.GalleryAssetActions.register({
			'name': 'setAsWorkspaceThumbnail',
			'text': otui.tr('Set as workspace thumbnail'),
			'img': 'style/img/asset_action/set_thumbnail_folder16.svg',
			'select': OTDCPWAssetActionsManager.setAsWorkspaceThumbnail,
			'setup': OTDCPWAssetActionsManager.setupWorkspaceThumbnail
		}, 8, 4);

	});
})(window);


