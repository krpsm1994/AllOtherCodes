(function (exports) {
	exports.OTDCPWConst = {
		SLIM_SCROLL_CONT_DIMENSIONS: { 'width': '100%', 'height': '100%' }
	};

	//Create Product Workspace View - Metadata View
	var OTDCPWMetadataView = exports.OTDCPWMetadataView =

		otui.extend("AssetMetaDataView", "OTDCPWMetadataView",
			function () {
				this.properties = {
					'name': 'OTDCPWMetadataView', // Name of the view
					'title': otui.tr("Metadata"),
					'type': 'OTDCPWMetadataView',
					'productData': []
				};

				this.renderTemplate = function () {
					// If the view is not loaded or rendered.
					//TODO check if this has any significance
					if (!this.internalProperty("loaded")) {
						return;
					}
					
					var newFolderNameInput = document.getElementById("ot-newfolder-name");
					if (newFolderNameInput) document.getElementById("ot-newfolder-name").setAttribute("placeholder", otui.tr("Enter name"));
					
					//Hide and show catalog, product lookup based on hybris connection availability and perform height adjustments accordingly
					if (OTDCPWWorkspaceManager.connection != "") {
						$(".otdcpw-hide-content").removeClass('otdcpw-hide-content');
						document.getElementById('otdcpw-metadata-type').style.height = 'calc(100% - 13rem)';
					} else {
						document.getElementById('otdcpw-metadata-type').style.height = 'calc(100% - 7rem)';
					}

					var selectedMetadataTypeId = OTDCPWWorkspaceManager.model;

					var contentArea, metadataJSON = {};

					if (!selectedMetadataTypeId) {
						return;
					}
					var data = { name: selectedMetadataTypeId };
					contentArea = this.contentArea();
					var self = this;

					otui.services.selectLookup.read({ 'lookup': "OTDC.PW.CATALOGVERSION.LOOKUP", 'asSelect': false }, function (domainValues) {
						var templatefrag = document.createElement("ot-select");
						for (var i = 0; i < domainValues.length; i++) {
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
						var metadataModelIdJSON = { "metadata_model_id": selectedMetadataTypeId };
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
					});

				};
				/**
				 * Set slim scroll for the rendered view
				 */
				this.setSlimScroll = function () {
					var contentArea = this.contentArea();
					var metadataArea = contentArea.find(".ot-new-folder-metadata");

					otui.slimscroll(metadataArea, OTDCPWConst.SLIM_SCROLL_CONT_DIMENSIONS);
				};

				/**
				 * Displays the validation message.
				 */
				this.displayError = function (validationMessage) {
					var errMsg = validationMessage || otui.tr("Unable to create workspace. Please correct the errors indicated in red, then retry.");
					AssetMetaDataView.prototype.displayError.call(this, errMsg);
				};

				this.bind("setup-finished", function () {
					var self = this;
					var inputEl = $(".otdcpw-catalogcol2").find("#otdcpw-create-pim-input");
					var inputFieldId = inputEl[0].id;
					if (inputEl) {
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
									empty: ['<div class="otdcpw-empty-message"><span style="padding:4px">No matching results found<span></div>']
								},
								display: 'value'
							});
						//On selecting a value, sync the data
						inputEl.bind('typeahead:select typeahead:autocomplete', function (event, option) {
							OTDCPWMetadataView.getProductData(option.id, function (success, response) {
								if (!response || !success) {
									return;
								}
								//Store product information for later use
								self.properties.productData = response;
								Array.prototype.forEach.call($(".ot-primary-metadata ot-metadata[editable]:not([ot-table-cell]):not(.ot-tabular-single-column-scalar)"), function (el) {
									var obj = response.find(function (element) { return element.id == el.fields[0] });
									if (obj && obj.value) {
										var def = OTMetadataElement.types[el.type];
										var value;
										if (el.type == 'date') {
											var intDate = parseInt(obj.value);
											value = new Date(intDate);
											//Fix added to populate date in create workspace dialog.. not removing this comment for future use 
											/*value = date.toISOString();
											 var editor = el.querySelector(".ot-metadata-editor");
											 if (editor) {
												editor.children[0].value = ((date.getMonth() > 8) ? (date.getMonth() + 1) :
											 		('0' + (date.getMonth() + 1))) + '/' + ((date.getDate() > 9) ? date.getDate() :
											 			('0' + date.getDate())) + '/' + date.getFullYear();
											 }*/
										} else {
											value = obj.value;
											value = obj.value.replace(/(<([^>]+)>)/ig, '');
										}
										def.inputValue.set.call(el, value, value);
										el.value = value;
									} else {
										if (obj && (obj.value == null || obj.value == "")) {
											var def = OTMetadataElement.types[el.type];
											def.inputValue.set.call(el, '', '');
										}
										if (el.type == 'date') {
											// def.inputValue.set.call(el,null);
											var editor = el.querySelector(".ot-metadata-editor");
											if (editor)
												editor.value = null;

										}
									}
								}
								);

							},false);
						});
					}
				});

			});

	//Create typeahead query
	OTDCPWMetadataView.createTypeAheadQuery = function createTypeAheadQuery(inputFieldId) {
		var searchResultsCache = {};
		return function (query, syncResults, asyncResults) {
			if (query.length >= OTDCPWWorkspaceManager.config.search.typeahead.minLength) {
				var data = {};
				data.inputFieldId = inputFieldId;
				data.input = query;
				data.catalogVersion = $(".otdcpw-pim-catalog ot-select").val();
				data.connection = OTDCPWWorkspaceManager.connection;
				var searchResultIndex = data.catalogVersion + ":" + query;

				if (searchResultsCache[searchResultIndex] === undefined) {
					// first time search, get search results remotely
					searchResultsCache[searchResultIndex] = "searchIsPending";
					this.loadValuesForTypeAheadSearch(
						data,
						function (success, result) {
							if (!success) {
								var error = otui.tr("Unable to execute the search. Please contact Administrator");
								otui.NotificationManager.showNotification({
									'message': error,
									'status': 'error',
									'stayOpen': true
								});
								return;
							} else {
								searchResultsCache[searchResultIndex] = result;
								asyncResults(result);
							}
						}.bind(this)
					);

				} else if ($.isArray(searchResultsCache[searchResultIndex])) {
					// data has already been loaded
					syncResults(searchResultsCache[searchResultIndex]);
				} else {
					// search call has been triggered but isn't yet returned -> nothing to do
				}
			}
		}.bind(this);

	}
	//Call api
	OTDCPWMetadataView.loadValuesForTypeAheadSearch = function loadValuesForTypeAheadSearch(data, callback) {
		var session = jQuery.parseJSON(sessionStorage.session);
		$.ajax({
			type: "POST",
			url: "/ot-damlink/api/pw/product/typeaheadsearch",
			data: {
				pimConnectionId: data.connection,
				searchText: data.input,
				catalogVersion: data.catalogVersion,
				loginName: session.login_name,
				messageDigest: session.message_digest,
				validationKey: session.validation_key,
				queryLang: otui.locale(),
				userId: session.user_id,
				sessionId: session.id
			},
			dataType: "json",
			success: function (json) {
				callback(true, json);
			},
			error: function (json) {
				callback(false, json);
			}
		});
	}

	OTDCPWMetadataView.isvalidProduct = true;
	OTDCPWMetadataView.getProductData = function getProductData(input, callback, resync) {
		var session = jQuery.parseJSON(sessionStorage.session);
		$.ajax({
			type: "POST",
			url: "/ot-damlink/api/pw/product/sync",
			data: {
				pimConnectionId: OTDCPWWorkspaceManager.connection,
				pk: input,
				loginName: session.login_name,
				messageDigest: session.message_digest,
				validationKey: session.validation_key,
				queryLang: otui.locale(),
				userId: session.user_id,
				sessionId: session.id,
				resync: resync
			},
			dataType: "json",
			success: function (json) {
				OTDCPWMetadataView.isvalidProduct = true;
				callback(true, json);
			},
			error: function (error) {
				if(error?.responseText){
					let errorMessage = otui.tr(error.responseText);
					otui.NotificationManager.showNotification({
						message: errorMessage,
						stayOpen: false,
						status: "error"
					});
					OTDCPWMetadataView.isvalidProduct = false;
				}
				callback(false, error);
			}
		});
	}

	//Create Product Workspace View - Security Policy View
	var OTDCPWSecurityView = exports.OTDCPWSecurityView =
		otui.extend("AssetUploadSecPolicyView", "OTDCPWSecurityView",
			function () {
				this.properties = {
					'name': 'OTDCPWSecurityView', // Name of the view
					'title': otui.tr("Security"),
					'type': 'OTDCPWSecurityView'
				};

				this.renderTemplate = function renderTemplate() {
					var contentArea;
					// If the view is not loaded or rendered.
					if (!this.internalProperty("loaded")) {
						return;
					}
					var assignedPolicies = undefined;

					contentArea = this.contentArea();
					var securityPolicyEl = contentArea.find("ot-security-policy");
					if (!securityPolicyEl || securityPolicyEl.length === 0) {
						return;
					}
					securityPolicyEl[0].assignedPolicies = assignedPolicies ? assignedPolicies : {};
					var newFolderSecurityArea = contentArea.find(".ot-new-folder-security");

					;
					var orientChange = function () {
						OTDCPWNewWorkspaceView.setHeight(newFolderSecurityArea);
					};
					$(window).on("orientationchange.dialog", orientChange);

					var folderSecurityWrapper = contentArea.find(".ot-new-folder-security");
					otui.OTMMHelpers.fixSecurityPolicyFiltersPosition(folderSecurityWrapper, contentArea);
				};

			});
	/**
	 * @class OTDCPWNewWorkspaceView
	 * @mixes withTabs
	 * @constructor
	 * @param {OTDCPWNewWorkspaceView} props Properties for this view.
	 * @classdesc
	 * 
	 */

	exports.OTDCPWNewWorkspaceView = otui.define("OTDCPWNewWorkspaceView", ['withTabs'],

		/** @lends OTDCPWNewWorkspaceView
		 *  @this OTDCPWNewWorkspaceView
		 */

		function () {
			this.properties =
			{
				'name': "New workspace",
				'title': otui.tr("Create Product Workspace")
			};
			this.hierarchy =
				[
					{ 'view': OTDCPWMetadataView },
					{ 'view': OTDCPWSecurityView }
				];

			this._initContent = function initNewWorkspaceView(self, callback) {
				var template = self.getTemplate("content");
				callback(template);
			};

			this.showInlineError = function (inputElWrapper, msg) {
				var errorEl = inputElWrapper.querySelector(".ot-error-msg");
				inputElWrapper.setAttribute("ot-in-error", "");
				errorEl.textContent = msg;
			};

			this.clearInlineError = function (inputElWrapper) {
				var errorEl = inputElWrapper.querySelector(".ot-error-msg");
				inputElWrapper.removeAttribute("ot-in-error");
				errorEl.textContent = "";
			};
		});

	/**
	 * 
	 * initiates the create workspace dialog.
	 * 
	 * @param data - saved data if any
	 * @param options - dialog options
	 * 
	 */
	OTDCPWNewWorkspaceView.show = function (data, options) {
		options = options || {};
		data = data || {};
		OTDCPWNewWorkspaceView.prototype.dialogProperties = OTDCPWNewWorkspaceView.prototype.dialogProperties || {};
		OTDCPWNewWorkspaceView.prototype.dialogProperties.save = options.save;
		if (Object.keys(data).length) {
			if (data.collection_resource && data.collection_resource.collection) {
				OTDCPWNewWorkspaceView.prototype.dialogProperties.savedValues = data.collection_resource.collection;
			}
			else {
				OTDCPWNewWorkspaceView.prototype.dialogProperties.savedValues = data;
			}
		}
		if (options.initialiseCallback) {
			OTDCPWNewWorkspaceView.prototype.dialogProperties.initialiseCallback = options.initialiseCallback;
		}
		OTDCPWNewWorkspaceView.prototype.dialogProperties.fromActivityContext = options.fromActivityContext;
		OTDCPWNewWorkspaceView.prototype.dialogProperties.isContextName = (data || {}).isContextName;
		OTDCPWNewWorkspaceView.prototype.dialogProperties.isContextUser = (data || {}).isContextUser;

		//Check if product workspace folder type is set
		if (OTDCPWWorkspaceManager.isInitialized === "false" || !OTDCPWWorkspaceManager.folder_type) {
			msg = otui.tr("Product Workspace folder type is not found. Please contact your administrator");
			otui.NotificationManager.showNotification({
				message: msg,
				stayOpen: false,
				status: "warning"
			});
			return;
		}
		//Check if has access for the product workspace folder type
		var folderTypes = FolderTypesManager.getFolderTypes();
		var hasAccess = false;
		for (var type in folderTypes) {
			if (type == OTDCPWWorkspaceManager.folder_type) {
				hasAccess = true;
				break;
			}
		}
		if (hasAccess) {
			otui.dialog("otdcpwcreate", options);
		}
		else {
			var notification = { 'message': otui.tr("You do not have access to the product workspace folder type. Please contact your administrator."), 'status': 'warning' };
			otui.NotificationManager.showNotification(notification);
			return;
		}
	};

	/**
	 * Calculate and set height
	 */
	OTDCPWNewWorkspaceView.setHeight = function (contentArea) {
		var otModalDialogBody = otui.parent(contentArea[0], ".ot-modal-dialog-body");
		var otTabs = otui.parent(contentArea[0], "ot-tabs");
		var otTabsHeader = otTabs.querySelector(".ot-tabs-header");
		var height = $(otModalDialogBody).height() - $(otTabsHeader).height();
		height = height + "px";
		contentArea.height(height);
	}
	/**
	 * Validates the input values, calls the save handler, also closes the dialog.
	 */
	OTDCPWNewWorkspaceView.createWorkspace = function (event, data) {
		
		if(!OTDCPWMetadataView.isvalidProduct){
			let errorMessage = otui.tr('A workspace already exists for the selected product, or the provided input is invalid.');
			otui.NotificationManager.showNotification({
				message: errorMessage,
				stayOpen: false,
				status: "error"
			});
			return;
		}
		data = data || {};
		var dialogElement = event.target;
		var viewObj = otui.Views.containing(dialogElement);

		//Validate folder name
		if (!isFolderNameValid.call(viewObj)) {
			return;
		} else {
			var folderNameEl = viewObj.contentArea().find('#ot-newfolder-name');
			var folderName = (folderNameEl && folderNameEl.length > 0) ? folderNameEl[0].value.trim() : "";
			data.name = folderName;
		}
		var newFolderDialogView = otui.Views.containing(event.target);
		var tabs = newFolderDialogView.childViews;
		var isValid = true;
		var tab;

		// Validate all the tabs before saving.
		for (var idx = 0; idx < tabs.length; idx++) {
			tab = tabs[idx];

			if (!tab.isValid()) {
				newFolderDialogView.properties.selected = tab.properties.name;
				var errMsg;
				if (newFolderDialogView.properties.dialogProp && newFolderDialogView.properties.dialogProp.options &&
					newFolderDialogView.properties.dialogProp.options.showFolderSelector) {
					errMsg = otui.tr("Unable to create the new workspace. Please correct the errors indicated in red, then retry.");
				}
				tab.displayError(errMsg);
				isValid = false;
				break;
			}
		}

		if (!isValid) {
			return;
		}

		//Check if duplicate checker enabled
		var duplicateCheckerEnabled = otui.SystemSettingsManager.getSystemSettingValue("ASSET", "CONFIG", "DUPLICATE_CHECKER_ENABLED");
		if (duplicateCheckerEnabled && duplicateCheckerEnabled.toLowerCase() === "true" && !viewObj.storedProperty("duplicateFolderValidated")) {
			var parentFolderId = OTDCPWWorkspaceManager.root_folder;
			checkForDuplicateFolders.call(viewObj, folderName, parentFolderId, function (folderCreate) {
				if (folderCreate)
					createFolder.call(viewObj, data);
			});
		}
		else {
			createFolder.call(viewObj, data);
		}

	};

	/**
	 * When SAP CC search value is cleared, clears the auto populated values on screen
	 */
	OTDCPWNewWorkspaceView.handlePimInputChange = function handlePimInputChange(event) {
		//When value is cleared in Search input bar, clear off editor
		if ($('ot-view[ot-view-type="OTDCPWNewWorkspaceView"] #otdcpw-create-pim-input')[0].value == '') {
			//Clear the synced values(if any) in editor
			Array.prototype.forEach.call($(".ot-primary-metadata ot-metadata[editable]:not([ot-table-cell]):not(.ot-tabular-single-column-scalar)"), function (el) {
				var editor = el.querySelector(".ot-metadata-editor");
				if (editor)
					editor.value = null;
			});
			//And also clear the response saved
			var metadataView = otui.Views.containing(event.target).getChildView(OTDCPWMetadataView);
			metadataView.properties.productData = [];
			OTDCPWMetadataView.isvalidProduct = true;
		}
	}
	/**
	 * 
	 * @param {object} data - Has folder name and used to build info required for folder creation
	 */
	var createFolder = function createFolder(data) {
		var viewObj = this;

		//Get metadata 
		var metadataView = this.getChildView("OTDCPWMetadataView");
		data.metadata = metadataView.getFilledMetadata("folder");

		//SAPDC-6593 Populate SAP CC key fields( Article number, product PK and Catalog Version ) based on Search response only if they are non editable
		if ($('ot-view[ot-view-type="OTDCPWNewWorkspaceView"] #otdcpw-create-pim-input')[0].value != '' && metadataView.properties.productData.length > 0) {
			var pimExcludeList = new Map();
			var pimIncludeList = ['OTDC.PW.PK', 'OTDC.PW.ARTICLE.NUMBER', 'OTDC.PW.CATALOGVERSION', 'OTDC.PW.TF.VARIANTPK','OTDC.PW.TF.VARIANTCODE', 'OTDC.PW.TF.VARIANTIDENTIFIER', 'OTDC.PW.TF.VARIANTATTRS'];
			var folderMetadata = (data.metadata.folder_resource.folder.metadata && data.metadata.folder_resource.folder.metadata.metadata_element_list) || [];

			//Check if already SAP CC key fields data available in folderMetadata(this happens when key fields are made editable), if yes add it to excluded list
			for (var i = 0; i < folderMetadata.length; i++) {
				if (folderMetadata[i].id == 'OTDC.PW.PK' || folderMetadata[i].id == 'OTDC.PW.ARTICLE.NUMBER' || folderMetadata[i].id == 'OTDC.PW.CATALOGVERSION') {
					pimExcludeList.set(folderMetadata[i].id, true);
				}
			}

			//Loop over PIM key fields and generate value for save if it is not part of exclude list
			pimIncludeList.forEach(function (value) {
				//Skip if this field is available in exclude list
				if (!pimExcludeList.get(value)) {
					//If not part of exclude list, then generate value 
					var obj = metadataView.properties.productData.find(function (element) { return element.id == value });
					if (obj && obj.value) {
						OTDCPWWorkspaceManager.generateValueforSave(obj.value, folderMetadata, obj.id);
					}
				}

			});
			if (folderMetadata.length > 0) {
				if (data.metadata.folder_resource.folder.metadata && data.metadata.folder_resource.folder.metadata.metadata_element_list) {
					data.metadata.folder_resource.folder.metadata.metadata_element_list = folderMetadata;
				}
			}
		}

		//Get Selected Security Policy
		var securityView = this.getChildView("OTDCPWSecurityView");
		var OTSecurityPolicyElement = securityView.contentArea().find('ot-security-policy');
		var assignedSecurityPolicies = OTSecurityPolicyElement[0].getAssignedSecurityPolicyIdList();
		data.assignedSecurityPolicies = assignedSecurityPolicies;

		//Call Handler
		this.dialogProperties.save.handler.call(this, data, viewObj);
	};

	/**
	 * Validates the folder name
	 * @returns true or false based on validation
	 */
	var isFolderNameValid = function isFolderNameValid() {
		var folderNameWrapperEl = this.contentArea()[0].querySelector(".ot-newfolder-input-wrapper");
		var folderNameEl = this.contentArea().find('#ot-newfolder-name');
		var folderName = (folderNameEl && folderNameEl.length > 0) ? folderNameEl[0].value.trim() : "";
		if (!folderName.length > 0) {
			this.showInlineError(folderNameWrapperEl, otui.tr("Workspace name cannot be empty."));
			var msg = otui.tr("Unable to create workspace. Please correct the errors indicated in red, then retry");
			otui.NotificationManager.showNotification({
				message: msg,
				stayOpen: false,
				status: "warning"
			});
			return false;
		} else {
			this.clearInlineError(folderNameWrapperEl);
			return true;
		}
	}
	/**
	 * Check if folder name is duplicated using AssetDetailManager and based on the response callback
	 */
	var checkForDuplicateFolders = function checkForDuplicateFolders(newFolderName, parentFolderId, callback) {
		var self = this;
		var folderNamesList = [newFolderName];
		var data = { "folderNameList": folderNamesList, "parentFolderId": parentFolderId };
		AssetDetailManager.checkDuplicateFolders(data, function (response, success) {
			self.storedProperty("duplicateFolderValidated", true);
			if (success && response) {
				var duplicateFoldersCount = ((response.assets_resource || {}).asset_list || []).length;
				if (duplicateFoldersCount > 0) {
					var buttonNames = [otui.tr("Continue"), otui.tr("Cancel")];
					var validationMessage = otui.tr("One or more workspaces with the same name already exist in the current folder. If you continue, a new folder will be created with the same name as the existing folder.");
					otui.confirm({ 'title': otui.tr("Confirm"), 'buttons': buttonNames, 'message': validationMessage, 'type': otui.alertTypes.CONFIRM },
						function (doit) {
							self.storedProperty("duplicateFolderValidated", true);
							if (doit) {
								callback(true);
							}
							else {
								callback(false);
							}
						});
				}
				else {
					callback(true);
				}
			}
			else {
				callback(true);
			}
		});
	}
})(window);
