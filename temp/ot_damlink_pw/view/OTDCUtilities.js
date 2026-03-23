(function (exports) {
	let OTDCUtilities = exports.OTDCUtilities = function OTDCUtilities() {
	};

	OTDCUtilities.defaultPWFacetName = 'OTDC.PW.FACET.CONFIG';
	OTDCUtilities.defaultBWFacetName = 'OTDCBW.FACETCONFIG';
	OTDCUtilities.defaultPWAssetFacetName = 'OTDC.PW.ASSET.FACET.CONFIG';
	//TODO: Need to change defaultAssetFacetName
	OTDCUtilities.defaultBWAssetFacetName = 'ARTESIA.FACET.DEFAULT CONFIG';
	OTDCUtilities.currentContextName = 'OTDC.CURRENTCONTEXT';
	OTDCUtilities.PWContextName = 'PW';
	OTDCUtilities.BWContextName = 'BW';
	OTDCUtilities.ModernUIHeaderParameters = {'isModernResultsView': true, 'showBreadcrumbPanel': false};


	//Function to return interval label for date range and date interval facets
	OTDCUtilities.getIntervalLabel = function(startDate, endDate) {
		if(startDate && endDate) {
			return otui.tr("{0} to {1}", otui.formatDate(new Date(startDate), otui.DateFormat.DATE), otui.formatDate(new Date(endDate), otui.DateFormat.DATE));
		} else if (startDate && !endDate) {
			return otui.tr("{0} to Present", otui.formatDate(new Date(startDate), otui.DateFormat.DATE));
		} else if (!startDate && endDate) {
			return otui.tr("Before {0}", otui.formatDate(new Date(endDate), otui.DateFormat.DATE));
		}
	}

	OTDCUtilities.addFacetToFacetRestrictions = function(view, facet) {
		let facetRestrictions = JSON.parse(view.properties.appliedFacets);
		let createSimpleFieldRestriction = true;
		let createNumericRestriction = true;
		let createNumericIntervalRestriction = true;
		let createDateIntervalRestriction = true;
		let createDateRestriction = true;
		let createCascadingRestriction = true;
		let createRefineByKeywordRestriction = true;

		//Check if facet is selected with other value and append current value to it
		for (const facetElement of facetRestrictions.facets) {
			if (facetElement.type == "com.artesia.search.facet.FacetSimpleFieldRestriction" && facetElement.field_id == facet.fieldID) {
				createSimpleFieldRestriction = false;
				facetElement.value_list.push(facet.value);
			}
			else if (facetElement.type == "com.artesia.search.facet.FacetNumericRangeFieldRestriction" && facetElement.field_id == facet.fieldID) {
				createNumericRestriction = false;
				facetElement.value_list.push(facet.value);
			}
			else if (facetElement.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction" && facetElement.field_id == facet.fieldID) {
				createNumericIntervalRestriction = false;
				facetElement.value_list.push(facet.value);
			}
			else if (facetElement.type == "com.artesia.search.facet.FacetDateIntervalFieldRestriction" && facetElement.field_id == facet.fieldID) {
				createDateIntervalRestriction = false;
				if (facet.value.custom_range) {
					facet.value.interval_label = OTDCUtilities.getIntervalLabel(facet.value.fixed_start_date, facet.value.fixed_end_date);
				}
				facetElement.value_list.push(facet.value);
			}
			else if (facetElement.type == "com.artesia.search.facet.FacetDateRangeFieldRestriction" && facetElement.field_id == facet.fieldID) {
				createDateRestriction = false;
				if (facet.value.custom_range) {
					facet.value.interval_label = OTDCUtilities.getIntervalLabel(facet.value.start_date, facet.value.end_date);
				} else {
					facet.value.interval_label = OTDCUtilities.getIntervalLabel(facet.value.start_date, facet.value.end_date);
				}
				facetElement.value_list.push(facet.value);
			}
			else if (facetElement.type == "com.artesia.search.facet.FacetCascadingFieldRestriction" && facetElement.field_id == facet.fieldID) {
				createCascadingRestriction = false;
				facetElement.value_list = [];
				facetElement.value_list.push(facet.value);
			}
			else if (facetElement.type == "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction" && facetElement.field_id == facet.fieldID) {
				createRefineByKeywordRestriction = false;
				facetElement.value_list = facet.value;
			}
		}

		//If facet value is not selected previously add it to facetRestrictions
		if (facet.type == "com.artesia.search.facet.FacetSimpleFieldRestriction" && createSimpleFieldRestriction) {
			facetRestrictions.facets.push({ 'name': facet.name, 'type': facet.type, 'facet_generation_behavior': facet.multiSelect ? 'EXCLUDE' : 'DROP', 'field_id': facet.fieldID, 'value_list': [facet.value] });
		}
		else if (facet.type == "com.artesia.search.facet.FacetNumericRangeFieldRestriction" && createNumericRestriction) {
			facetRestrictions.facets.push({ 'name': facet.name, 'type': facet.type, 'facet_generation_behavior': facet.multiSelect ? 'EXCLUDE' : 'DROP', 'field_id': facet.fieldID, 'value_list': [facet.value] });
		}
		else if (facet.type == "com.artesia.search.facet.FacetNumericIntervalFieldRestriction" && createNumericIntervalRestriction) {
			facetRestrictions.facets.push({ 'name': facet.name, 'type': facet.type, 'facet_generation_behavior': facet.multiSelect ? 'EXCLUDE' : 'DROP', 'field_id': facet.fieldID, 'value_list': [facet.value] });
		}
		else if (facet.type == "com.artesia.search.facet.FacetDateIntervalFieldRestriction" && createDateIntervalRestriction) {
			if (facet.value.custom_range) {
				facet.value.interval_label = OTDCUtilities.getIntervalLabel(facet.value.fixed_start_date, facet.value.fixed_end_date);
			}
			facetRestrictions.facets.push({ 'name': facet.name, 'type': facet.type, 'facet_generation_behavior': facet.multiSelect ? 'EXCLUDE' : 'DROP', 'field_id': facet.fieldID, 'value_list': [facet.value] });
		}
		else if (facet.type == "com.artesia.search.facet.FacetDateRangeFieldRestriction" && createDateRestriction) {
			if (facet.value.custom_range) {
				facet.value.interval_label = OTDCUtilities.getIntervalLabel(facet.value.start_date, facet.value.end_date);
			} else {
				facet.value.interval_label = OTDCUtilities.getIntervalLabel(facet.value.start_date, facet.value.end_date);
			}
			facetRestrictions.facets.push({ 'name': facet.name, 'type': facet.type, 'facet_generation_behavior': facet.multiSelect ? 'EXCLUDE' : 'DROP', 'field_id': facet.fieldID, 'value_list': [facet.value] });
		}
		else if (facet.type == "com.artesia.search.facet.FacetCascadingFieldRestriction" && createCascadingRestriction) {
			facetRestrictions.facets.push({ 'name': facet.name, 'type': facet.type, 'facet_generation_behavior': 'DRILLDOWN', 'field_id': facet.fieldID, 'value_list': [facet.value] });
		}
		else if (facet.type == "com.artesia.search.facet.FacetRefineByKeywordFieldRestriction" && createRefineByKeywordRestriction) {
			facetRestrictions.facets.push({ 'name': 'Keyword', 'type': facet.type, 'facet_generation_behavior': 'EXCLUDE', 'field_id': facet.fieldID, 'value_list': facet.value });
		}
		else if (facet.type == FacetsManager.colorPalette) {
			facetRestrictions.facets.push(
				{
					'name': facet.name,
					'type': facet.type,
					'facet_generation_behavior': facet.multiSelect ? 'EXCLUDE' : 'DROP',
					'field_id': facet.fieldID,
					'value_list': [facet.value],
					'label': facet.label
				}
			);
		}
		return facetRestrictions;
	}

	OTDCUtilities.doFolderChildrenRead = function doFolderChildrenRead(data, callback, view) {
		let assetIdPathMapRequestParam;
		let nodeID = data.nodeID;
		let virtFolderId = data.virtFolderId;
		let cachedData = FolderManager.getCachedFolderData(nodeID);

		//consider pageID(custom unique ID) while updating pageManager 
		let pageID = data.pageID || nodeID;
		let after = function after(folderData) {
			let showDeletedAssetsPrefValue, showDeletedAssets;
			if(!view){
				view = AssetActions.getCurrentView();
			}
			view.storedProperty("folderData", folderData);

			let preferenceName = PageManager.getFieldPreferenceName(view.properties.templateName);
			let extraFields = PageManager.getExtraFields();

			//If this is called from assign to product consider extrafields
			if (view instanceof OTDCPWAssignToProductView) {
				extraFields = view.properties.metadataFields;
			}

			let pageProperties = data.pageProperties;
			let assetsPerPage = +(pageProperties.assetsPerPage);

			//show/ hide deleted assets
			showDeletedAssets = view.properties.showDeletedAssets;
			if (showDeletedAssets === undefined) {
				showDeletedAssetsPrefValue = otui.PreferencesManager.getPreferenceDataById('ARTESIA.PREFERENCE.RESULTSVIEW.SHOW_DELETED_ASSETS');
				if (showDeletedAssetsPrefValue && showDeletedAssetsPrefValue[0] && showDeletedAssetsPrefValue[0].values && showDeletedAssetsPrefValue[0].values[0]) {
					showDeletedAssets = showDeletedAssetsPrefValue[0].values[0];
				}
			}

			OTDCUtilities.getAssets(data.nodeID, data.pageProperties, assetsPerPage, preferenceName, extraFields, callback, data.isWidget, showDeletedAssets, view.properties.isMosaic || view.constructor.name === 'WidgetContentsDialogView',view);
		};

		let folderSidebarView = typeof FolderSidebarView === 'undefined' ? undefined : otui.main.getChildView(FolderSidebarView);
		let fullPath = false;
		if (folderSidebarView && !folderSidebarView.getChildView(FolderView).model.find(nodeID, virtFolderId)) {
			cachedData = undefined;
			FolderManager.clearCachedFolderDataForID(nodeID);
			fullPath = true;
		}

		let breadCrumb = data.breadcrumb;
		if (breadCrumb && breadCrumb.ids && breadCrumb.ids.length > 1) {
			let parentId = breadCrumb.ids[breadCrumb.ids.length - 2];
			let treeId = breadCrumb.ids[0];
			treeId = treeId.substring("0", treeId.length - 1);
			assetIdPathMapRequestParam = { "asset_id_path_map": [{ "assetId": nodeID, "paths": [{ "complete": false, "parents": [{ "children": [{ "container_state": "NORMAL", "id": nodeID, "original_uoi_id": nodeID, "sequence_number": 0 }], "container_state": "NORMAL", "id": parentId, "original_uoi_id": parentId, "sequence_number": 0 }], "sequence_number": 0, "tree_descriptor": { "detached": false, "tree_id": treeId } }] }] }
		}
		if (cachedData && nodeID !== RecentArtifactsManager.RECENT_FOLDERS) {
			//Update total children as sometimes it is holding wrong data
			PageManager.numTotalChildren[pageID] = cachedData.container_child_counts.total_child_count;
			after(cachedData);
		}
		else if (nodeID === RecentArtifactsManager.RECENT_FOLDERS) {
			otui.services.recentfolderswithpathlist.read({ 'nodeID': nodeID }, callback, this);
		}
		else {
			OTDCUtilities.readFolderData(nodeID, pageID, assetIdPathMapRequestParam, fullPath, function (response, success) {
				if (!success)
					callback(response, false, 0);
				else
					after(response);
			});
		}
	}

	OTDCUtilities.getAssets = function getAssets(folderID, pageProperties, pageSize, preferenceID, extraFields, callback, isWidget, showDeletedAssets, isMosaic, view) {
		let pageNumber = parseInt(pageProperties.page);
		let serviceUrl = "/folders/" + folderID + "/children/";
		if (isWidget) {
			serviceUrl = serviceUrl + "?load_type=full&data_load_request=" + encodeURIComponent('{"data_load_request":{"load_thumbnail_info":"true","load_preview_info":"true","load_container_originating_urls":"true"}}') + "&after=" + pageNumber * pageSize + "&limit=" + pageSize;
		} else if (isMosaic) {
			if(view instanceof OTDCPWHomeView || view instanceof OTDCBWHomeView){
				serviceUrl = serviceUrl + "?load_type=custom&data_load_request="
			} else {
				serviceUrl = serviceUrl + "?load_type=full&data_load_request="
			}
			serviceUrl = serviceUrl
				+ encodeURIComponent('{"data_load_request":{"load_asset_content_info":"true","load_thumbnail_info":"true","load_preview_info":"true","load_container_originating_urls":"true","load_subscribed_to":"true","metadata_fields_to_return":"ARTESIA.FIELD.TAG"}}') + "&after=" + pageNumber * pageSize + "&limit=" + pageSize;
		} else {
			if(view instanceof OTDCPWHomeView || view instanceof OTDCBWHomeView){
				serviceUrl = serviceUrl + "?load_type=custom&load_multilingual_values=true&level_of_detail=slim&after=" + pageNumber * pageSize + "&limit=" + pageSize;
			} else {
				serviceUrl = serviceUrl + "?load_type=full&load_multilingual_values=true&level_of_detail=slim&after=" + pageNumber * pageSize + "&limit=" + pageSize;
			}
			
		}

		if (preferenceID && !isWidget)
			serviceUrl += "&preference_id=" + encodeURIComponent(preferenceID);

		if (extraFields && extraFields.length)
			serviceUrl += "&metadata_to_return=" + encodeURIComponent(extraFields.join(","))

		if (pageProperties.sortField && pageProperties.sortField != "undefined") {
			let prefix = pageProperties.sortPrefix;
			if (prefix && prefix[prefix.length - 1] != "_")
				prefix += "_";
			serviceUrl += "&sort=" + prefix + pageProperties.sortField;
		}

		if (showDeletedAssets && showDeletedAssets === "false") {
			serviceUrl += "&asset_filter_request=" + encodeURIComponent('{"asset_filter_request_param":{"asset_filter_request":{"exclude_deleted_assets":true}}}');
		}
		serviceUrl += '&data_load_request='+ encodeURIComponent('{"data_load_request":{"load_asset_content_info":"true","load_thumbnail_info":"true","load_preview_info":"true","load_container_originating_urls":"true","load_subscribed_to":"true","load_video_rollover_info":"true"}}');

		otui.get(otui.service + serviceUrl, undefined, otui.contentTypes.json, function (response, status, success, context) {
			let jResponse = response;
			if (success) {
				let results = jResponse.folder_children.asset_list;
				AssetManager.prepareGalleryMetadata(results);
				callback(results, success);
			}
			else {
				callback(response, success);
			}
		});
	};

	/**
	 * Read folder data and update FolderManager cache
	 * @param {*} nodeID 
	 * @param {*} pageID 
	 * @param {*} assetIdPathMapRequestParam 
	 * @param {*} fullPath 
	 * @param {*} callback 
	 * @returns 
	 */
	OTDCUtilities.readFolderData = function readFolderData(nodeID, pageID, assetIdPathMapRequestParam, fullPath, callback) {
		let readFolderDataReq = {};
		if (nodeID === RecentArtifactsManager.RECENT_FOLDERS) {
			otui.services.recentfolderswithpathlist.read({ 'nodeID': nodeID }, callback, this);
			return;
		}
		let dataLoadRequest, showDeletedAssets;
		if (fullPath) {
			dataLoadRequest = { 'child_count_load_type': 'both', 'load_path_with_children': true };
		}
		else {
			dataLoadRequest = { 'child_count_load_type': 'both', 'load_path': true };
		}
		// We need to get the number of child assets and folders for this folder
		let serviceUrl = "/folders/" + nodeID + '?load_type=custom&data_load_request=' + encodeURIComponent(JSON.stringify({ 'data_load_request': dataLoadRequest }));

		let showDeletedAssetsPrefValue = otui.PreferencesManager.getPreferenceDataById('ARTESIA.PREFERENCE.RESULTSVIEW.SHOW_DELETED_ASSETS');
		if (showDeletedAssetsPrefValue && showDeletedAssetsPrefValue[0] && showDeletedAssetsPrefValue[0].values && showDeletedAssetsPrefValue[0].values[0]) {
			showDeletedAssets = showDeletedAssetsPrefValue[0].values[0];
		}

		if (showDeletedAssets && showDeletedAssets === "false") {
			serviceUrl += "&asset_filter_request=" + encodeURIComponent('{"asset_filter_request_param":{"asset_filter_request":{"exclude_deleted_assets":true}}}');
		}

		if (readFolderDataReq[serviceUrl]) {
			readFolderDataReq[serviceUrl].push(callback);
		}
		else {
			readFolderDataReq[serviceUrl] = [callback];

			otui.get(otui.service + serviceUrl, undefined, otui.contentTypes.json, function (response, status, success) {
				if (!success) {
					readFolderDataReq[serviceUrl].forEach(function (cb) {
						cb(response, false);
					});
				}
				else {
					PageManager.numTotalChildren[pageID] = response.folder_resource.folder.container_child_counts.total_child_count;
					let folderData = response.folder_resource.folder;
					FolderManager.updateFolderData(nodeID, folderData);

					readFolderDataReq[serviceUrl].forEach(function (cb) {
						cb(folderData, true);
					});

				}
				readFolderDataReq[serviceUrl] = null;
			});
		}
	}

	/**
	 * Fetches the BO Types related to the given BO Type ID and returns the result in the callback function
	 * @param {*} callback - callback function to return the response
	 * @param {*} boTypeId - the BO Type ID for which related BO Types are to be fetched
	 * @param {*} view - the view context in which this function is called
	 */
	OTDCUtilities.fetchBOTypes = function fetchBOTypes(callback, boTypeId, view) {
		view.blockContent();
		$.ajax({
			type: "GET",
			url: "/ot-damlink/api/business-object-types/" + boTypeId + "/related",
			contentType: 'application/json',
			success: function (json) {
				view.unblockContent();
				if (callback) {
					callback.call(json);
				}
			},
			error: function (error) {
				view.unblockContent();
				otui.NotificationManager.showNotification({
					message: otui.tr(error.responseText),
					stayOpen: true,
					status: "error"
				});
				//TODO : Mock data for now to proceed with development, need to remove once service is ready
				let boTypesResponse = {
					relatedBOTypes: [
						{
							boTypeId: "BUS2007",
							displayName: "Maintenance Order"
						},
						{
							boTypeId: "BUS0010",
							displayName: "Functional Location"
						},
						{
							boTypeId: "MATERIAL",
							displayName: "Material"
						},
						{
							boTypeId: "EQUIPMENT",
							displayName: "Equipment"
						}
					]
				}
				if (callback) {
					callback.call(this, boTypesResponse);
				}
			}
		});
	}


	OTDCUtilities.fetchRelatedWorkspaces = function fetchRelatedWorkspaces(callback, view,  boDetails) {
		view.blockContent();
		const data = {
			relatedBOType: boDetails.boTypeId,
			offset: boDetails.offset,
			limit: boDetails.limit,
			sortBy: boDetails.sortBy,
			sortOrder: boDetails.sortOrder
		};
		$.ajax({
			type: "GET",
			url: "/ot-damlink/api/workspaces/" + boDetails.workspaceId + "/related?",
			data: data,
			contentType: 'application/json',
			success: function (response) {
				view.unblockContent();
				if (callback) {
					callback.call(response);
				}
			},
			error : function (error) {
				view.unblockContent();
				otui.NotificationManager.showNotification({
					message: otui.tr(error.responseText),
					stayOpen: true,
					status: "error"
				});
				//TODO: Replace with actual service call to fetch related workspaces based on workspace Id provided
				//Mocked data for now
				let relatedWorkspacesResponse = {
					pagination: {
						offset: 0,
						limit: 25,
						totalCount: 45
					},
					workspaces: []
						/*{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						},
						{
							workspaceId: "8209bf4094001e12143b71c4c249eb6b91e0971a",
							boId: "4000123456",
							boName: "Routine Maintenance",
							boDesc: "Routine Maintenance Desc"
						}
					]*/
				};
				if (callback) {
					callback.call(this, relatedWorkspacesResponse);
				}
			}
		});
	}

	/**
	 *  Check if current context is PW
	 * @returns true if current context is PW else false
	 */
	OTDCUtilities.isPWContext = function () {
		if (OTDCPWWorkspaceManager.isInitialized != "true") {
			return false;
		}
		const currentContext = localStorage.getItem(OTDCUtilities.currentContextName);
		return currentContext === OTDCUtilities.PWContextName;
	}

	/**
	 *  Check if current context is BW
	 * @returns true if current context is BW else false
	 */
	OTDCUtilities.isEAMContext = function () {
		if (OTDCBWWorkspaceManager.isInitialized != "true") {
			return false;
		}
		const currentContext = localStorage.getItem(OTDCUtilities.currentContextName);
		return currentContext === OTDCUtilities.BWContextName;
	}

	/**
	 * Open or close all variants accordions
	 */
	OTDCUtilities.openAndCloseAllVariants = function openAndCloseAllVariants() {
		let variantcollapseIconHeader = $('#otdcpwCollapseAllIcon');
		if (variantcollapseIconHeader.hasClass('otdcpw-accordion-collapse-all-icon')) {
			variantcollapseIconHeader.removeClass('otdcpw-accordion-collapse-all-icon');
			variantcollapseIconHeader.addClass('otdcpw-accordion-open-all-icon');
			variantcollapseIconHeader.attr("title", otui.tr("Collapse All"));
			$.each(OTDCUtilities.variantsJson, function (index, variant) {
				let sanitizedVariantCode = OTDCUtilities.sanitizeIdForHtml(variant.code);
				let arrowIcon = $('#' + sanitizedVariantCode + '_arrow');
				let row = $('#' + sanitizedVariantCode + '_row');
				let accordionContent = $('#' + sanitizedVariantCode+'_attributes');
				accordionContent.removeClass('hide');
				arrowIcon.removeClass('otdcpw-accordion-icon-up-arrow');
				arrowIcon.addClass('otdcpw-accordion-icon-down-arrow');
				row.removeClass('border_bottom');
				arrowIcon.attr("title", otui.tr("Collapse"));
			})
		} else {
			variantcollapseIconHeader.addClass('otdcpw-accordion-collapse-all-icon');
			variantcollapseIconHeader.removeClass('otdcpw-accordion-open-all-icon');
			variantcollapseIconHeader.attr("title", otui.tr("Expand All"));
			$.each(OTDCUtilities.variantsJson, function (index, variant) {
				let sanitizedVariantCode = OTDCUtilities.sanitizeIdForHtml(variant.code);
				let arrowIcon = $('#' + sanitizedVariantCode + '_arrow');
				let row = $('#' + sanitizedVariantCode + '_row');
				let accordionContent = $('#' + sanitizedVariantCode+'_attributes');
				accordionContent.addClass('hide');
				arrowIcon.addClass('otdcpw-accordion-icon-up-arrow');
				arrowIcon.removeClass('otdcpw-accordion-icon-down-arrow');
				row.addClass('border_bottom');
				arrowIcon.attr("title", otui.tr("Expand"));
			})
		}
	}
	
	/**
	 * Toggle the accordion for a specific variant
	 * @param {*} element 
	 */
	OTDCUtilities.toggleAccordion = function toggleAccordion(element) {
		let variantId = element.currentTarget?.id;
		let arrowIcon = $('#' + variantId+'_arrow');
		let row = $('#' + variantId + '_row');
		let accordionContent = $('#' + variantId+ '_attributes');
	
		if (accordionContent.hasClass('hide')) {
			accordionContent.removeClass('hide');
			arrowIcon.removeClass('otdcpw-accordion-icon-up-arrow');
			arrowIcon.addClass('otdcpw-accordion-icon-down-arrow');
			row.removeClass('border_bottom');
			arrowIcon.attr("title", otui.tr("Collapse"));
		} else {
			accordionContent.addClass('hide');
			arrowIcon.addClass('otdcpw-accordion-icon-up-arrow');
			arrowIcon.removeClass('otdcpw-accordion-icon-down-arrow');
			row.addClass('border_bottom');
			arrowIcon.attr("title", otui.tr("Expand"));
		}
		let upArrows = document.getElementsByClassName('otdcpw-accordion-icon-up-arrow');
		let downArrows = document.getElementsByClassName('otdcpw-accordion-icon-down-arrow');
		let collapseAllIcon = $('#otdcpwCollapseAllIcon');
		if (upArrows.length === OTDCUtilities.variantsJson.length) {
			collapseAllIcon.removeClass('otdcpw-accordion-open-all-icon');
			collapseAllIcon.addClass('otdcpw-accordion-collapse-all-icon');
			collapseAllIcon.attr("title", otui.tr("Expand All"));
		} else if (downArrows.length === OTDCUtilities.variantsJson.length) {
			collapseAllIcon.removeClass('otdcpw-accordion-collapse-all-icon');
			collapseAllIcon.addClass('otdcpw-accordion-open-all-icon');
			collapseAllIcon.attr("title", otui.tr("Collapse All"));
		}
	}
	
	/**
	 * Get the attributes element for a specific variant
	 * @param {*} attributes 
	 * @param {*} variantCode 
	 * @returns 
	 */
	OTDCUtilities.getAttributesElement = function getAttributesElement(attributes, variantCode) {
		let sanitizedVariantCode = OTDCUtilities.sanitizeIdForHtml(variantCode);
		let attributesDiv = $('<div>').attr('id',sanitizedVariantCode+"_attributes").addClass('otdcpw-attributes-div');
		if(attributes.length === 1 && attributes[0] === '') {
			let noAttributesDiv = $('<div>').addClass('otdcpw-attributes-table').text(otui.tr('No attributes.')).attr('title', otui.tr('No attributes.'));
			attributesDiv.append(noAttributesDiv);
		} else {
			let attributesTable = $('<table>').addClass('otdcpw-attributes-table');
			$.each(attributes, function(index,attribute){
				if(attribute !== ''){
					let keyValue = attribute.split(':');
					let attributesRow = `
						<tr id="${sanitizedVariantCode+'_attributes_' + keyValue[0]}">
							<td id="${sanitizedVariantCode+'_attributes_' + keyValue[0] + '_key'}" class="otdcpw-attributes-td-first-child" title="${keyValue[0]}">${keyValue[0]}</td>
							<td id="${sanitizedVariantCode+'_attributes_' + keyValue[0]+'_value'}" class="otdcpw-attributes-td" title="${keyValue[1]}">${keyValue[1]}</td>
						</tr>
					`;
					attributesTable.append(attributesRow);
				}
			});
			attributesDiv.append(attributesTable);
		}
		return attributesDiv;
	}

	/**
	 * Sanitize an ID for use in HTML
	 * @param {*} id 
	 * @returns 
	 */
	OTDCUtilities.sanitizeIdForHtml = function sanitizeIdForHtml(id) {
		// Replace special characters with _
		let sanitizedId = id.replace(/[^a-zA-Z0-9\-_]/g, "_");
		
		// Ensure it doesn't start with a number
		if (/^\d/.test(sanitizedId)) {
			sanitizedId = "_" + sanitizedId;
		}
	
		return sanitizedId;
	}

	OTDCUtilities.variantsJson = [];
	OTDCUtilities.selectedVariants = [];

	/**
	 * Check if all variants are selected
	 * @returns true if all variants are selected else false
	 */
	OTDCUtilities.checkIfAllVariantsSelected = function checkIfAllVariantsSelected() {
		return OTDCUtilities.variantsJson.every(variant => variant.selected === true);
	}

	/**
	 * Generate the variants table
	 * @param {*} fgMetadata 
	 * @param {*} selectedVariants 
	 * @returns 
	 */
	OTDCUtilities.generateVariantsTable = function (fgMetadata, selectedVariants) {
		let variantsArray = fgMetadata[0].metadata_element_list;

		// Determine the maximum number of values in any field
		let maxLength = Math.max(
			variantsArray[0]?.values?.length || 0,
			variantsArray[1]?.values?.length || 0,
			variantsArray[2]?.values?.length || 0
		);

		// Generate the transformed JSON array
		OTDCUtilities.variantsJson = [];
		OTDCUtilities.selectedVariants = selectedVariants || [];
		for (let i = 0; i < maxLength; i++) {
			OTDCUtilities.variantsJson.push({
				"code": variantsArray[0]?.values[i]?.value?.value,
				"identifier": variantsArray[1]?.values[i]?.value?.value || "",
				"attributes": variantsArray[2]?.values[i]?.value?.value || "",
				"selected": OTDCUtilities.selectedVariants.includes(variantsArray[0]?.values[i]?.value?.value) || false
			});
		}

		let rightViewDiv = $('<div>').addClass('otdcpw-right-view');
		let variantsView = $('<div>').addClass('otdcpw-variants-view');
		let variantsHeader = $('<div>').addClass('otdcpw-variants-header');

		let variantsHeaderContent = `
			<span class="otdcpw-variants-header-name">
				<span class="otdcpw-checkbox">
					<input type="checkbox" class="ot-facet-simple-checkbox" id="otdcpw-select-all-checkbox" ${OTDCUtilities.checkIfAllVariantsSelected()? 'checked' : ''}
						onClick="OTDCPWVariantsLookupView.onSelectAllCheckboxChange(event)">
				</span>
				<ot-i18n>Variant Code</ot-i18n>
				<ot-i18n ot-as-title>Variant Code</ot-i18n>
			</span>
			<span class="otdcpw-variants-header-name">
				<ot-i18n>Variant Identifier</ot-i18n>	
				<ot-i18n ot-as-title>Variant Identifier</ot-i18n>
			</span>
			<span id="otdcpwCollapseAllIcon" class="otdcpw-accordion-collapse-all-icon" onClick="OTDCUtilities.openAndCloseAllVariants()">
				<ot-i18n ot-as-title>Expand All</ot-i18n>
			</span>
		`;
		variantsHeader.append(variantsHeaderContent);
		variantsView.append(variantsHeader);

		let variantsContent = $('<div>').addClass('otdcpw-variants-content');

		$.each(OTDCUtilities.variantsJson, function (index, variant) {
			let sanitizedVariantCode = OTDCUtilities.sanitizeIdForHtml(variant.code);
			let attributeContent = variant.attributes.includes(';') ? OTDCUtilities.getAttributesElement(variant.attributes.split(';'), variant.code) : OTDCUtilities.getAttributesElement([variant.attributes], variant.code);
			let variantDiv = $('<div>').attr('id', sanitizedVariantCode).addClass('otdcpw-variant').on('click', OTDCUtilities.toggleAccordion);
			let rowHtml = `
                <div class="otdcpw-variants-content-row border_bottom" id="${sanitizedVariantCode + '_row'}">
                    <span class="otdcpw-variants-content-value" title="${sanitizedVariantCode}">
						<span class="otdcpw-checkbox">
							<input type="checkbox" class="ot-facet-simple-checkbox" id="otdcpw-select-${sanitizedVariantCode}" ${variant.selected ? 'checked' : ''}
								onClick="OTDCPWVariantsLookupView.onCheckboxChange(event, '${sanitizedVariantCode}')">
						</span>
						${variant.code}
					</span>
                    <span class="otdcpw-variants-content-value" title="${variant.identifier}">${variant.identifier}</span>
                    <span class="otdcpw-accordion-icon-up-arrow" title="Expand" id="${sanitizedVariantCode + "_arrow"}"></span>
                </div>
            `;
			let attributeContentDiv = $('<div>').attr('id', sanitizedVariantCode + '_attributes').addClass('accordion-content').addClass('hide').addClass('border_bottom');

			attributeContentDiv.append(attributeContent);
			variantDiv.append(rowHtml);
			variantDiv.append(attributeContentDiv);
			variantsContent.append(variantDiv);
		});
		variantsView.append(variantsContent);
		rightViewDiv.append(variantsView);
		return rightViewDiv;
	}

})(window);