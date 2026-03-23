(function(exports) {
	
	var OTDCPWWorkspaceManager = exports.OTDCPWWorkspaceManager = function OTDCPWWorkspaceManager(){ 
	};
	/**Constants */
	OTDCPWWorkspaceManager.METADATA_TABULAR = "com.artesia.metadata.MetadataTable";
	OTDCPWWorkspaceManager.METADATA_TABULAR_FIELD = "com.artesia.metadata.MetadataTableField";
	OTDCPWWorkspaceManager.METADATA_FIELD = "com.artesia.metadata.MetadataField";
	OTDCPWWorkspaceManager.CONTAINER = "com.artesia.container.Container";
	/**
	 * 
	 * @param {*} dataObj - Metadata and Security policy entered by user on create WP dialog
	 * @param {*} view - Create Workspace dialog
	 */
	OTDCPWWorkspaceManager.CreateWorkspace = function(dataObj, view) {
		var folder_resource = {
			"name": dataObj.name,
			"type": OTDCPWWorkspaceManager.folder_type,
			"folderID": OTDCPWWorkspaceManager.root_folder,
			"metadata": dataObj.metadata,	
			"securityPolicies": dataObj.assignedSecurityPolicies
		};
		
		//FolderManager.createFolder(folder_resource);
		var serviceUrl = otui.service + "/folders/" + folder_resource.folderID;

    	var data = FolderManager.createFolderRep(folder_resource);

		otui.post(serviceUrl, JSON.stringify(data), otui.contentTypes.json, function(response, status, success)
		{
			var message = otui.tr("Workspace created successfully.");
			var stayOpen = false;
			var status = "ok";

			if (!success)
			{	
				message = otui.tr("Unable to create new Workspace.");				
				stayOpen = true;
				status = "error";
			}
			else{
				//Create Uploads SubFolder
				if(!(response.folder_resource && response.folder_resource.folder)){
					otui.NotificationManager.showNotification({
						message: otui.tr("Failed to create subfolder within workspace."),
						stayOpen: true,
						status: "error",
					});
					return;
				} 
				
				var securityPolicyList = [];
				response.folder_resource.folder.security_policy_list.forEach(function (item){
					securityPolicyList.push(item.id);
				});
				var subFolder_resource = {
					name: OTDCPWWorkspaceManager.subFolder_name,
					type: OTDCPWWorkspaceManager.subFolder_type,
					folderID: response.folder_resource.folder.asset_id,
					securityPolicies: securityPolicyList,
				};

				var subFolderserviceUrl =
				otui.service + "/folders/" + subFolder_resource.folderID;

				var subFolderdata = FolderManager.createFolderRep(subFolder_resource);

				otui.post(subFolderserviceUrl,JSON.stringify(subFolderdata),otui.contentTypes.json,
					function (response, status, success) {
						if (!success) {
							otui.NotificationManager.showNotification({
								message: otui.tr("Failed to create subfolder within workspace."),
								stayOpen: true,
								status: "error",
							});
						}
				});
			}	
			var view = otui.Views.containing($(".ot-results")[0]);
			if(!view){
				view = AssetActions.getCurrentView();
			}
			//clear facets if any and reload the view
			view.properties.appliedFacets = JSON.stringify({'facets': []});
			view.reload();

    		otui.NotificationManager.showNotification({
				'message' : message,
				'stayOpen' : stayOpen,
				'status' : status
			});
    		
    	});
		otui.DialogUtils.cancelDialog(view.contentArea()[0], true);	
	};
	
	/**
	 * Fetch Product Workspace Configurations
	 */
	OTDCPWWorkspaceManager.getRootConfigs = function getRootConfigs(callback){
		//API call
		var session = jQuery.parseJSON(sessionStorage.session);		
		$.ajax({
			type: "POST",
			url: "/ot-damlink/api/workspace/config?type=pw",
			data: {
				loginName: session.login_name,
				sessionId: session.id,
				messageDigest: session.message_digest,
				validationKey: session.validation_key,
				queryLang: otui.locale(),
				userId: session.user_id
			},
			async: true,
			dataType: "json",
			success: function (response) {
				OTDCPWWorkspaceManager.isInitialized = response.otdcpw.ui.isInitialized;
				OTDCPWWorkspaceManager.folder_type = response.otdcpw.ui.workspace.folderType.id;
				OTDCPWWorkspaceManager.root_folder = response.otdcpw.ui.rootFolder.id;
				OTDCPWWorkspaceManager.model = response.otdcpw.ui.workspace.model;
				//Sub Folder Configs
				OTDCPWWorkspaceManager.subFolder_name = response.otdcpw.ui.workspace.subfolder[0].name;
				OTDCPWWorkspaceManager.subFolder_type = response.otdcpw.ui.workspace.subfolder[0].folderTypeId;
				//PIM configs
				OTDCPWWorkspaceManager.connection = response.otdcpw.ui.pim.connectionId;
				OTDCPWWorkspaceManager.config = {};
				OTDCPWWorkspaceManager.config.facetConfig = response.otdcpw.ui.facetConfig || OTDCUtilities.defaultPWFacetName;
				OTDCPWWorkspaceManager.config.assetFacetConfig = response.otdcpw.ui.assetFacetConfig || OTDCUtilities.defaultPWAssetFacetName;
				//Extracting mark for assign config
				OTDCPWWorkspaceManager.config.markForAssign = {};
				OTDCPWWorkspaceManager.config.markForAssign.maxProducts = {};
				var maxProducts = response.otdcpw.ui.markForAssign.maxProducts;
				try {
					maxProducts = parseInt(maxProducts);
					if (!maxProducts)
						maxProducts = 100;
				} catch (error) {
					maxProducts = 100;
				}
				OTDCPWWorkspaceManager.config.markForAssign.maxProducts = maxProducts;
				OTDCPWWorkspaceManager.config.markForAssign.codeFldCriteria = (response.otdcpw.ui.pim.markForAssign.fieldMap.code
					|| 'AUTO.TABULAR.GROUP/AUTO.TABULAR.PRODUCT ID[OTDC.PW.ASSET.FG.ASSIGNMENT]');
				OTDCPWWorkspaceManager.config.markForAssign.sysIdFldCriteria = (response.otdcpw.ui.pim.markForAssign.fieldMap.systemId
					|| 'AUTO.TABULAR.GROUP/AUTO.TABULAR.SYSTEM ID[OTDC.PW.ASSET.FG.ASSIGNMENT]');
				OTDCPWWorkspaceManager.config.markForAssign.attrIdFldCriteria = (response.otdcpw.ui.pim.markForAssign.fieldMap.attrId
					|| 'AUTO.TABULAR.GROUP/AUTO.TABULAR.ATTRIBUTE ID[OTDC.PW.ASSET.FG.ASSIGNMENT]');
				OTDCPWWorkspaceManager.config.markForAssign.singleSelectablePIMPosition = (response.otdcpw.ui.pim.markForAssign.singleSelectablePIMPosition
					|| '');
				//PIM config
				OTDCPWWorkspaceManager.config.pim = {};
				OTDCPWWorkspaceManager.config.pim.systemId = (response.otdcpw.ui.pim.systemId
					|| '');
				OTDCPWWorkspaceManager.config.pim.syncFields = (response.otdcpw.ui.pim.syncFields || []);
				if (OTDCPWWorkspaceManager.config.pim.syncFields.length == 0) {
					OTDCPWWorkspaceManager.config.pim.syncFields = ["OTDC.PW.PK", "OTDC.PW.CATALOGVERSION", "OTDC.PW.PRODUCT.STATE", "OTDC.PW.IDENTIFIER",
						"OTDC.PW.ARTICLE.NUMBER", "OTDC.PW.DESCRIPTION", "OTDC.PW.SUMMARY", "OTDC.PW.EAN", "OTDC.PW.MANUFACTURER",
						"OTDC.PW.DATE.FROM", "OTDC.PW.DATE.TO"];
				}
				OTDCPWWorkspaceManager.config.pim.mediaAttr = {};
				OTDCPWWorkspaceManager.config.pim.mediaAttr.defaultLookupTable = (response.otdcpw.ui.pim.mediaAttr.defaultLookupTable
					|| 'OTDC.PW.ASSET.MEDIAATTR.LOOKUP');
				OTDCPWWorkspaceManager.config.pim.mediaAttr.fieldId = (response.otdcpw.ui.pim.mediaAttr.fieldId
					|| 'OTDC.PW.ASSET.PIMMEDIAATTR[OTDC.PW.ASSET.FG.ASSIGNMENT]');
				//Extracting Assign To Product Configuration
				OTDCPWWorkspaceManager.config.AssignToProduct = {};
				OTDCPWWorkspaceManager.config.AssignToProduct.maxWorkspaces = {};
				var maxWorkspaces = response.otdcpw.ui.assignToProduct.maxWorkspaces;
				try {
					maxWorkspaces = parseInt(maxWorkspaces);
					if (!maxWorkspaces)
						maxWorkspaces = 10;
				} catch (error) {
					maxWorkspaces = 10;
				}
				OTDCPWWorkspaceManager.config.AssignToProduct.maxWorkspaces = maxWorkspaces;
				//Extracting Search Configuration
				OTDCPWWorkspaceManager.config.search = {};
				OTDCPWWorkspaceManager.config.search.typeahead = {};
				var minLength = response.otdcpw.ui.pim.search.typeahead.minLength;
				try {
					minLength = parseInt(minLength);
					if (!minLength)
						minLength = 3;
				} catch (error) {
					minLength = 3;
				}
				OTDCPWWorkspaceManager.config.search.typeahead.minLength = minLength;
				var maxSuggestions = response.otdcpw.ui.pim.search.typeahead.maxSuggestions;
				try {
					maxSuggestions = parseInt(maxSuggestions);
					if (!maxSuggestions)
						maxSuggestions = 50;
				} catch (error) {
					maxSuggestions = 50;
				}
				OTDCPWWorkspaceManager.config.search.typeahead.maxSuggestions = maxSuggestions;

				//variants Lookup Config
				otui.MetadataModelManager.addFieldLookup('OTDC.PW.ASSET.TF.PIMVARIANT', function(event){
					const urlParams = new URLSearchParams(window.location.search);
                	const param1 = urlParams.get('p');
					let start = param1.indexOf("otdcWorkspace/") + "otdcWorkspace/".length;
					let end = param1.indexOf("@", start);
					let workspaceId = param1.substring(start, end);
					let data = { 'assetID': workspaceId, 'unwrap': true };
					if(workspaceId && param1.startsWith('otdcWorkspace')){
						OTDCPWDetailEditView.showVariantsDialog(data, event);
					} else {
						otui.NotificationManager.showNotification({
							'message': otui.tr('This feature is currently available only on the Product Dashboard.'),
							'status': 'error',
							'stayOpen': true
						});
					}
				});

				callback();
			},
			error: function(response){
				OTDCPWWorkspaceManager.isInitialized = response.otdcpw.ui.isInitialized;
				console.error("Error reading the workspace configs");
				callback();
			}
		});
	}
	
	/**Read folder children */
	OTDCPWWorkspaceManager.readFolderChildren = function readFolderChildren(data,callback){
		var showDeletedAssets;
		// Construct the URL to the /assets REST api
		var url = otui.service + "/folders/" + data.id + "/children/";

		// limit our results to 30 assets using "limit" parameter
		url += "?load_type=custom";

		// Append the selection context parameter in JSON format
		url += "&data_load_request=" +
		encodeURIComponent(JSON.stringify(data.selection));

		//Check the preference for deleted assets
		var showDeletedAssetsPrefValue = otui.PreferencesManager.getPreferenceDataById('ARTESIA.PREFERENCE.RESULTSVIEW.SHOW_DELETED_ASSETS');
    	if(showDeletedAssetsPrefValue && showDeletedAssetsPrefValue[0] && showDeletedAssetsPrefValue[0].values && showDeletedAssetsPrefValue[0].values[0])
		{
			showDeletedAssets = showDeletedAssetsPrefValue[0].values[0];
		}
		
		if(showDeletedAssets && showDeletedAssets === "false")
		{
			url += "&asset_filter_request=" + encodeURIComponent('{"asset_filter_request_param":{"asset_filter_request":{"exclude_deleted_assets":true}}}');
		}

		// get the results
		// response = JSON results received from server
		// status = HTTP status received from server
		// success = indicates overall success/failure
		otui.get( url, undefined, otui.contentTypes.json,
			function (response, status, success ){
				if (!success)
					callback(response, false);
				else
				{
					// Successful, unpack the results
					var assets = [];
					if (response && response.folder_children && response.folder_children.asset_list )
					{
						assets = response.folder_children.asset_list;
					}
					callback(assets, true);
				}
		});
	}

	OTDCPWWorkspaceManager.generateValueforSave = function generateValueforSave(value, metadataList, id){
		var id = id;
		var fieldInfo = otui.MetadataModelManager.rationaliseFieldObject({'id' : id})
		
		var type;
		if(fieldInfo.type == OTDCPWWorkspaceManager.METADATA_TABULAR_FIELD){
			type = "table";
		}else{
			type = getDataTypeMap(fieldInfo.data_type);
		}
		
		if(type=="table"){				
			metadataList.push({'id' : id, "type": "com.artesia.metadata.MetadataTableField", "values": generateSaveValueForStringTableField(id, value)});
		} else {
			metadataList.push({'id' : id, "type": "com.artesia.metadata.MetadataField", "value": generateSaveValue(id, type, value)});
		}
        // else{			
		// 	var values = [];
		// 	Array.prototype.forEach.call(el.querySelectorAll("ot-chiclet"), function(el){
		// 		var value = JSON.parse(el.value);
		// 		values.push({"id" : el.id, "displayValue" : el.getAttribute("displayValue"), "value" : value.value});
		// 	});
		// 	var columns = fieldInfo.metadata_element_list;
			
		// 	columns.forEach(function(col)
		// 	{
		// 		var listOfValues = [];

		// 		values.forEach(function(val)
		// 		{	
		// 			//TODO sometimes displayvalue is also passed
		// 			listOfValues.push(generateSaveValue(col.id, col.type, val.id, col.editType));
		// 		});

		// 		metadataList.push({'id' : col.id, 'type' : 'com.artesia.metadata.MetadataTableField', 'values' : listOfValues});
		// 	});
				
		// }
	}

    function getDataTypeMap(dataType){
		var type;
		switch (dataType) {
			case "CHAR":
				type = "string";
				break;
			case "DATE":
				type = "datetime";
				break;
			case "NUMBER":
				type = "number";
				break;
			default:
				type = "string"
				break;
		}
		return type;
	}

    function generateSaveValue(id,type,value,editType,displayValue){
		if(type == 'datetime' && value){
            // if(el.type == 'date'){
            var intDate = parseInt(value);
            var date = new Date(intDate);
            value = date.toISOString().split('T')[0];
            value = value + 'T00:00:00Z';
        } else if(value){
            value = value.replace( /(<([^>]+)>)/ig, '');
        }
			
		value = otui.MetadataModelManager.formatMetadataValue(type, type, value);
		type = otui.MetadataModelManager.getOTMMValueType(id, type, value);
		var obj = null;
		
		if (value || value === 0)
			{
				obj = {"value" : { "type": type, "value": value} };
				if(displayValue)
				{
					if ((editType === 'typeahead') || (editType === 'select'))
					{
						obj = {"value" : { "field_value": { "type": type, "value": value } } };
						obj.value.type = "com.artesia.metadata.DomainValue";
						obj.value.displayValue = displayValue;
					} else if((editType === 'cascade') || (editType === 'cascadetypeahead'))
					{
						obj = {"value" : { "field_value": { "type": type, "value": value } } };
						obj.value.type = "com.artesia.metadata.CascadingDomainValue";
						obj.value.displayValue = displayValue;
					}
				}
			}
		return obj;
	}

	/**
	 * Converts the comma separated base64 encoded table field values into an array OTMM API expects for table fields.
	 * @param {*} id OTMM Field ID
	 * @param {*} value Comma separated Base64 endoded table field values
	 * @param {*} type only string type is supported
	 * @returns 
	 */
	function generateSaveValueForStringTableField(id, value, type){
		if(!value){
			return [];
		}

		var tableFldValues = [];
		try{
			value.split(",").forEach((record, index) => {
				tableFldValues.push({"value" : { "type": "string", "value": atob(record)} });
			});
		} catch(error){
			//Ignore saving this value if there are any decoding errors
			console.log(error);
		}
		return tableFldValues;
	}
})(window);