(function (exports) {
	/**
	 * @class OTDCPWMarkForAssignmentView
	 * @mixes withContent
	 * @constructor
	 * @param {OTDCPWMarkForAssignmentView} props Properties for this view.
	 * @classdesc
	 *
	 */
	exports.OTDCPWMarkForAssignmentView = otui.define("OTDCPWMarkForAssignmentView", ['withContent'],
		function () {
			this.properties =
			{
				'name': "OTDCPWMarkForAssignmentView",
				'title': otui.tr("Mark for assignment"),
				'type': 'markforassignment',
				'service': 'markforassignment',
				'serviceData': {},
				'headerTemplate': 'header',
				'rowTemplate': 'row',
				'tableSelector': '.ot-table-content',
				'assetMediaMap': new Map()
			};

			if (otui.is(otui.modes.DESKTOP)) {
				this.properties.showPagination = false;
				this.properties.showLatestPagination = true;
				this.properties.showAssetsPerPageSetting = false;
			}

			this._initContent = function _initContent(self, callback) {
				var contentBuilder = function (location) {
					location = $(location);
					callback(location);

					//Only show relevant views and hide the others
					if (OTDCPWMarkForAssignmentView.dialogProperties.showSelectProductsArea) {
						$('#otdcpwMarkForAssignFinishBtn').hide();
						$('.otdcpw-markforassign-pim-assignment').hide();
					} else {
						$('#otdcpwMarkForAssignNextBtn').hide();
						$('.otdcpw-markforassign-select-products').hide();
						$('#otdcpw-markforassign-stepper').hide();
					}
					$('.otdcpw-markforassign-back-button').hide();

					_load.call(self, location);
				};

				buildTable.call(this, contentBuilder);
				createGlobalPimPositionDropdown();
			};

			this.columns = {};

			this.rowLocation = function rowLocation() {
				var physicalContent = this.internalProperty('physicalContent');
				return physicalContent.find('.ot-table-allrows').first();
			};

			this.applyStyles = function applyStyles(row, resource) {
				var fieldElements = row.querySelectorAll(".ot-table-element");
				for (var i = 0; i < fieldElements.length; ++i) {
					var columnNode = fieldElements[i];
					var columnName = columnNode.getAttribute('ot-fields');

					columnNode.setAttribute("role", "gridcell");

					//	If ot-fields is not set then use name.
					if (!columnName)
						columnName = columnNode.getAttribute('name');

					if (columnName) {
						var columnObject = this.columns[columnName];
						if (columnObject) {
							if (columnObject.scrolladjust)
								columnNode.setAttribute('style', columnObject.scrolladjust);
							else
								columnNode.setAttribute('style', columnObject.css);

							if (columnObject.drawFunction)
								columnObject.drawFunction(this, columnName, columnNode, resource);
						}
					}
				}
			}

			this.addRowFromTemplate = function addRowFromTemplate(resource) {
				var rowTemplateName = this.storedProperty("rowTemplate");
				var templateName = this.getTemplate(rowTemplateName, resource);
				var appliedTemplate = otui.Templates.applyObject(templateName, resource);

				this.applyStyles(appliedTemplate, resource);

				var result = appliedTemplate.querySelector(".ot-row-result");
				result.resourceData = resource;
				return result;
			};

			this.reload = function reload() {
				var location = this.contentArea();
				_load.call(this, location);
			}

			this.fixTableHeaderAndRowsWidth = function () {
				var width = 0;
				var contentArea = this.contentArea();

				contentArea.find(".ot-table-row").first().children().each(function () {
					width += $(this).width();
				});

				width = Math.max(width, contentArea.find(".ot-table-content").width());

				contentArea.find(".ot-table-header, .ot-table-row").each(function () {
					$(this).width(width);
				})
			};
		});
	OTDCPWMarkForAssignmentView.show = function (data, options) {
		options = options || {};
		data = data || {};
		OTDCPWMarkForAssignmentView.dialogProperties = data;
		otui.dialog("otdcpwmarkforassignmentdialog", options.viewProperties);
	};

	OTDCPWMarkForAssignmentView.onClickNext = function (event) {
		if (OTDCPWMarkForAssignmentView.validateProductList()) {
			//Showing only mark for assignment area
			$('.otdcpw-markforassign-select-products').hide();
			$('.otdcpw-markforassign-pim-assignment').show();

			//Show Finish and Cancel buttons
			$('.otdcpw-markforassign-back-button').show();
			$('#otdcpwMarkForAssignFinishBtn').show();
			$('#otdcpwMarkForAssignNextBtn').hide();

			//update stepper status colors and icons
			$('#otdcpw-markforassign-firstSvg').removeClass('otdcpw-markforassign-stepper-svg-active').addClass('otdcpw-markforassign-stepper-svg-completed');
			$('#otdcpw-markforassign-firstHrTag').removeClass('otdcpw-markforassign-stepper-hr-current-solid').addClass('otdcpw-markforassign-stepper-hr-completed');
			$('#otdcpw-markforassign-secondSvg').removeClass('otdcpw-markforassign-stepper-svg-pending').addClass('otdcpw-markforassign-stepper-svg-active');
			$('#otdcpw-markforassign-secondHrTag').removeClass('otdcpw-markforassign-stepper-hr-future-dashed').addClass('otdcpw-markforassign-stepper-hr-current-dashed');
			$('#otdcpw-markforassign-selectProductsText').removeClass('otdcpw-markforassign-stepper-icon-text-bold');
			$('#otdcpw-markforassign-pimAssignmentText').addClass('otdcpw-markforassign-stepper-icon-text-bold');
		} else {
			if (!OTDCPWMarkForAssignmentView.dialogProperties.productList.length) {
				var notification = { 'message': otui.tr("Please enter product ID to proceed."), 'status': 'warning' };
				otui.NotificationManager.showNotification(notification);
			} else {
				var notification = { 'message': otui.tr("Number of products cannot exceed {0}", OTDCPWWorkspaceManager.config.markForAssign.maxProducts), 'status': 'warning' };
				otui.NotificationManager.showNotification(notification);
			}
		}
	};

	/**
	* Validates product list input area. Also parses the input and sets it to dialogProperties. 
	*/
	OTDCPWMarkForAssignmentView.validateProductList = function (event) {
		OTDCPWMarkForAssignmentView.dialogProperties.productList = [];
		var productListVal = $('.otdcpw-markforassign-select-products > .otdcpw-products-list-input').val();
		if (!productListVal || productListVal.trim() === '') {
			return false;
		}
		productListVal = productListVal.trim().replace(/(\r\n|\r|\n)/g, ',');
		var rawProductList = productListVal.split(',');
		var productList = [];
		for (var index = 0; index < rawProductList.length; index++) {
			var parsedProductId = rawProductList[index].trim();
			if (parsedProductId !== '') {
				productList.push(parsedProductId);
			}
		}
		//Limit check on product IDs entered
		OTDCPWMarkForAssignmentView.dialogProperties.productList = productList;
		if (productList.length > 0 && productList.length <= OTDCPWWorkspaceManager.config.markForAssign.maxProducts) {
			return true;
		}
		return false;
	};

	OTDCPWMarkForAssignmentView.validateGlobalPIMpositions = function validateGlobalPIMpositions(event) {
		let pimPositionDropdown = $('div.otdcpw-markforassign-global-pimposition-dropdown').find('div.otdcpw-detail-col-div');
		let lookupDiv = pimPositionDropdown.length > 0 && pimPositionDropdown.find('div.ot-tabular-single-lookup');
		let chickletsDiv = lookupDiv.length > 0 && lookupDiv.find('div.ot-tabular-chiclets-section');
		//validate if PIM positions are selected for Apply/Replace actions
		if (!(chickletsDiv.length > 0 && chickletsDiv[0].children.length > 0)) {
			var notification = { 'message': otui.tr("Unable to proceed, please correct the error(s) indicated in red."), 'status': 'warning' };
			otui.NotificationManager.showNotification(notification);
			let errorDiv = lookupDiv.length > 0 && lookupDiv.find('div.ot-error-msg');
			errorDiv.length > 0 && errorDiv[0].children.length === 0 && errorDiv[0].append(errorElement());
			return false;
		}
		return true;
	}

	//function render chicklets at asset level based on Apply/Replace actions - simplified method
	function renderChickletsOnGlobalPIMSelection(optionEl, selectEl) {
		let chickletsSectionDiv = selectEl.nextElementSibling;
		let chicletElExists = chickletsSectionDiv.querySelector('ot-chiclet[ot-id="' + CSS.escape(optionEl.value) + '"]');

		if (chicletElExists)
			return;

		var chicletEl = document.createElement("ot-chiclet");
		chicletEl.setAttribute('displayValue', optionEl.text);
		chicletEl.id = optionEl.value;
		chicletEl.value = JSON.stringify({ 'value': optionEl.text });
		chicletEl.readOnly = false;
		//Register on click for chiclet - Perfome remove operation
		chicletEl.addEventListener("click", function (event) {
			var option = JSON.parse(event.currentTarget.value);
			var valueEntry = {
				"value": event.currentTarget.id,
				"displayValue": option.value
			};
			var chicletElExists = chickletsSectionDiv.querySelector('ot-chiclet[ot-id="' + CSS.escape(valueEntry.value) + '"]');
			if (chicletElExists) {
				otui.remove(chicletElExists);
				optionEl.selected = false;
			}
		})
		chickletsSectionDiv.append(chicletEl);
	}

	OTDCPWMarkForAssignmentView.onClickApply = function onClickApply(event) {
		if (OTDCPWMarkForAssignmentView.validateGlobalPIMpositions(event)) {
			var view = otui.Views.containing($('div.otdcpw-markforassign-global-pimposition-dropdown'));
			var contentArea = view.contentArea();
			//Iterate through the assets to assign pim positions
			OTDCPWMarkForAssignmentView.dialogProperties.resourceList.forEach(asset => {
				var assetDiv = contentArea.find('ot-resource[resourceid="' + asset.asset_id + '"]');
				var pimMediaDiv = assetDiv.find('.otdcpw-pim-position');
				var detailColDiv = pimMediaDiv.find('.otdcpw-detail-col-div');
				var singleLookupDiv = detailColDiv.find('.ot-tabular-single-lookup');
				var selectEl = singleLookupDiv.find('ot-select');
				for (var i = 0; i < selectEl[0].optionList.length; i++) {
					//validation to check selected pim positions chicklets are not already selected at asset level
					if (OTDCPWMarkForAssignmentView.selectedDomainValues.indexOf(selectEl[0].optionList[i].value) !== -1) {
						selectEl[0].optionList[i].selected = true;
						renderChickletsOnGlobalPIMSelection(selectEl[0].optionList[i], selectEl[0]);
					}
				}
			})
			//remove error message on Apply action
			var errorSections = document.getElementsByClassName('otdcpw-error-msg');
			errorSections.forEach(errorSection => errorSection.remove());
		}
	}

	OTDCPWMarkForAssignmentView.onClickReplace = function onClickReplace(event) {
		if (OTDCPWMarkForAssignmentView.validateGlobalPIMpositions(event)) {
			let singleSelectedPIMPositions = OTDCPWWorkspaceManager.config.markForAssign.singleSelectablePIMPosition;
			var view = otui.Views.containing($('div.otdcpw-markforassign-global-pimposition-dropdown'));
			var contentArea = view.contentArea();
			//Iterate through the assets to assign pim positions
			OTDCPWMarkForAssignmentView.dialogProperties.resourceList.forEach(asset => {
				let chickletsToDelete = [];
				var assetDiv = contentArea.find('ot-resource[resourceid="' + asset.asset_id + '"]');
				var pimMediaDiv = assetDiv.find('.otdcpw-pim-position');
				var detailColDiv = pimMediaDiv.find('.otdcpw-detail-col-div');
				var singleLookupDiv = detailColDiv.find('.ot-tabular-single-lookup');
				var selectEl = singleLookupDiv.find('ot-select');
				let chickletsSectionDiv = selectEl[0].nextElementSibling;

				//add existing chicklets to delete list if selected
				chickletsSectionDiv.childNodes.forEach(chicklet => {
					if (singleSelectedPIMPositions.indexOf(chicklet.getAttribute('ot-id')) === -1) {
						chickletsToDelete.push(chicklet);
					}
				});

				//chicklet will be removed when clicked on it, hence using click() which also updates dropdown values
				chickletsToDelete.forEach(chicklet => chicklet.click());

				for (var i = 0; i < selectEl[0].optionList.length; i++) {
					//validation to check selected pim positions chicklets are not already selected at asset level
					if (OTDCPWMarkForAssignmentView.selectedDomainValues.indexOf(selectEl[0].optionList[i].value) !== -1) {
						selectEl[0].optionList[i].selected = true;
						renderChickletsOnGlobalPIMSelection(selectEl[0].optionList[i], selectEl[0]);
					}
				}
			});
			//remove error message on Replace action
			var errorSections = document.getElementsByClassName('otdcpw-error-msg');
			errorSections.forEach(errorSection => errorSection.remove());
		}
	}

	OTDCPWMarkForAssignmentView.onClickBack = function onClickBack(event) {
		//Showing only select products area
		$('.otdcpw-markforassign-select-products').show();
		$('.otdcpw-markforassign-pim-assignment').hide();

		//Show Back, Next and Cancel buttons
		$('#otdcpwMarkForAssignFinishBtn').hide();
		$('#otdcpwMarkForAssignNextBtn').show();
		$('.otdcpw-markforassign-back-button').hide();

		//Update stepper css and icons
		$('#otdcpw-markforassign-firstSvg').removeClass('otdcpw-markforassign-stepper-svg-completed').addClass('otdcpw-markforassign-stepper-svg-active');
		$('#otdcpw-markforassign-firstHrTag').removeClass('otdcpw-markforassign-stepper-hr-completed').addClass('otdcpw-markforassign-stepper-hr-current-solid');
		$('#otdcpw-markforassign-secondSvg').removeClass('otdcpw-markforassign-stepper-svg-active').addClass('otdcpw-markforassign-stepper-svg-pending');
		$('#otdcpw-markforassign-secondHrTag').removeClass('otdcpw-markforassign-stepper-hr-current-dashed').addClass('otdcpw-markforassign-stepper-hr-future-dashed');
		$('#otdcpw-markforassign-pimAssignmentText').removeClass('otdcpw-markforassign-stepper-icon-text-bold');
		$('#otdcpw-markforassign-selectProductsText').addClass('otdcpw-markforassign-stepper-icon-text-bold');
	}

	OTDCPWMarkForAssignmentView.onClickFinish = function onClickFinish(event) {
		var view = otui.Views.containing(event.target);
		var resultData = OTDCPWMarkForAssignmentView.dialogProperties.resourceList;
		var variantList = OTDCPWMarkForAssignmentView.dialogProperties.variantList;
		var contentArea = view.contentArea();
		var bulkEdit = new Map();
		var isValidForm = true;
		for (var i = 0; i < resultData.length; i++) {
			var asset = resultData[i];
			var rowObject = contentArea.find('ot-resource[resourceid="' + asset.asset_id + '"]');
			var pimMediaDiv = rowObject.find(".otdcpw-pim-position");
			var values = [];
			Array.prototype.forEach.call(pimMediaDiv[0].querySelectorAll("ot-chiclet"), function (el) {
				values.push(el.id);
			});
			//Sort and stringify the array
			values.sort();
			var valuesString = values.toString();
			//Remove error div if already present
			var errorEl = pimMediaDiv[0].querySelector(".otdcpw-error-msg");
			if (errorEl) {
				errorEl.remove();
			}
			if (valuesString == "") {
				//Show error
				var notification = { 'message': otui.tr("Unable to proceed, please correct the error(s) indicated in red."), 'status': 'warning' };
				otui.NotificationManager.showNotification(notification);
				pimMediaDiv[0].appendChild(errorElement());
				isValidForm = false;
				break;
			}
			//Check if its same as asset level PIM media value
			if (view.properties.assetMediaMap.get(asset.asset_id) === valuesString) {
				valuesString = "OTDCPW_ASSET_DEFAULT_MEDIA_ATTR";
			}

			// Extract variant list assigned at asset metadata level only if this action is initiated from PW
			if(!OTDCPWMarkForAssignmentView.dialogProperties.showSelectProductsArea){
				//Replace product id with variants list if applied at asset metadata
				let variantVals = OTDCPWAssetActionsManager.getMetadataTableFieldValues(asset, 'OTDC.PW.ASSET.TF.PIMVARIANT', 'OTDC.PW.ASSET.PIMVARIANT', 'OTDC.PW.ASSET.FG.ASSIGNMENT');
				if(variantVals && variantVals.length>0){
					// Validate applied variant details at asset metadata
					let appliedVariantsList = OTDCPWAssetActionsManager.convertTableFldValuesToSimpleArray(variantVals);
					if(!OTDCPWAssetActionsManager.validateArrayDataWithMaster(appliedVariantsList, variantList)){
						var notification = { 'message': otui.tr("The variant details provided for the PIM variant field are invalid for the asset: {0}. Please review and correct the details.", asset.name), 'status': 'error' };
						otui.NotificationManager.showNotification(notification);
						isValidForm = false;
						break;
					}

					// Convert variant values as string
					let sortedVariantValsString = OTDCPWAssetActionsManager.sortAndFormatTableFldValues(variantVals);
					if(sortedVariantValsString){
						//Append it to map key
						valuesString = valuesString + '|' + sortedVariantValsString;
					}
				}
			}

			var resourceObject = bulkEdit.get(valuesString) || {};
			if (resourceObject.assetList) {
				resourceObject.assetList.push(asset.asset_id);
			} else {
				resourceObject.assetList = [];
				resourceObject.assetList.push(asset.asset_id);
				resourceObject.pimMedia = values;
			}
			bulkEdit.set(valuesString, resourceObject)
		}

		if (isValidForm) {
			var productList;
			if (OTDCPWMarkForAssignmentView.dialogProperties.showSelectProductsArea) {
				productList = OTDCPWMarkForAssignmentView.dialogProperties.productList;
			} else {
				productList = [];
				productList.push(OTDCPWMarkForAssignmentView.dialogProperties.productId);
			}
			OTDCPWAssetActionsManager.performMarkForAssignment(view, bulkEdit, productList)
		}
	}

	function errorElement() {
		var divEl = document.createElement("div");
		divEl.setAttribute("class", "otdcpw-error-msg");
		divEl.textContent = otui.tr("PIM position should have a value.");
		return divEl;
	}

	function buildTable(callback) {
		var self = this;
		var templateName = self.storedProperty("contentTemplate") || "content";
		var content = self.getTemplate(templateName, undefined);
		var headerTemplate = self.storedProperty("headerTemplate");
		var tableHeaderTemplate = self.getTemplate(headerTemplate, undefined);
		var tableSelector = self.storedProperty('tableSelector');
		var tableDiv = content.querySelector(tableSelector);
		var headerElement = tableHeaderTemplate.querySelector('.ot-table-header');

		tableDiv.setAttribute("role", "grid");

		if (!headerElement) {
			headerElement = document.createElement('div');
			headerElement.classList.add("ot-table-header");
			otui.Templates.insert(headerElement, tableHeaderTemplate);
		}

		var childList = headerElement.querySelectorAll('div');
		for (var i = 0; i < childList.length; ++i) {
			var columnNode = childList[i];
			var columnName = columnNode.getAttribute('name');

			columnNode.setAttribute("role", "columnheader");

			if (columnName) {
				var columnDetails = this.columns[columnName];
				if (!columnDetails) {
					var columnDetails = new Object();
					this.columns[columnName] = columnDetails;
				}

				columnDetails.name = columnName;
				if (!columnNode.hasAttribute("ot-style"))
					columnDetails.css = columnNode.getAttribute('style');
				else {
					columnDetails.css = parseStyle(columnNode.getAttribute("ot-style"));
					columnNode.setAttribute("style", columnDetails.css);
				}

				columnDetails.scrolladjust = parseStyle(columnNode.getAttribute('scrolladjust'));

				columnDetails.fixWidth = columnNode.hasAttribute("ot-fix-width") && !(ResultsView.tablePreferences[self.properties.name] && ResultsView.tablePreferences[self.properties.name][columnName] && ResultsView.tablePreferences[self.properties.name][columnName].width);

				if (columnDetails.fixWidth)
					columnDetails.width = 0;
			}

			var sortable = columnNode.getAttribute('sortable');
			if (columnName && sortable == "true") {
				columnNode.addEventListener('click', function (event) {
					var theNode = event.target;
					if ($(theNode).hasClass("ot-table-resizer"))
						return;
					//this conditon verfies closest div with ot-table-header-element class
					theNode = $(theNode).closest('.ot-table-heading')[0];
					var viewObject = otui.Views.containing(theNode);

					var hasSortField = theNode.hasAttribute("sort-field");
					//Need to add custom attribute with "datatype" to the date fields, otherwise this column consider string or number type.
					// Default sorting order for date is descending and Others ascending
					// Date : date
					// String : string
					// Number : number
					var dataType = theNode.getAttribute('datatype');

					var theName = theNode.getAttribute(hasSortField ? 'sort-field' : 'name');
					var sorted = theNode.getAttribute('sorted');

					if (sorted === 'desc') {
						sorted = 'asc';
					}
					else if (sorted === 'asc') {
						sorted = 'desc';
					}
					else if (!sorted && dataType === "date") {
						sorted = 'desc';
					}
					else {
						sorted = 'asc';
					}
					viewObject.setSort(theName, sorted);
				}, true);
			}

			//Currently we are not supporting column resizing
			if (self.properties.allowColumnResizing) {
				var columnResizer = document.createElement("span");
				columnResizer.className = "ot-table-resizer";
				var minWidth = 50;
				$(columnResizer).draggable({
					axis: 'x',
					appendTo: '.ot-table-content',
					helper: function () {
						var columnLine = $("<span class='ot-resize-rule'></span>");

						return columnLine;
					},
					drag: function (event, ui) {
						event.stopPropagation();
						var parentElement = event.target.parentElement;
						var parentLeft = parentElement.offsetLeft;
						var parentName = parentElement.getAttribute("name");
						var width = ui.position.left - parentLeft;

						if (width < minWidth)
							ui.position.left = parentLeft + minWidth;
					},
					stop: function (event, ui) {
						event.stopPropagation();
						var parentElement = event.target.parentElement;
						var parentLeft = parentElement.offsetLeft;
						var parentName = parentElement.getAttribute("name");
						var width = ui.position.left - parentLeft;

						if (!ResultsView.tablePreferences[self.properties.name])
							ResultsView.tablePreferences[self.properties.name] = {};

						if (!ResultsView.tablePreferences[self.properties.name][parentName])
							ResultsView.tablePreferences[self.properties.name][parentName] = {};

						self.columns[parentName].width = width;
						ResultsView.tablePreferences[self.properties.name][parentName].width = width;

						otui.PreferencesManager.setPreferenceById("ARTESIA.PREFERENCE.GENERAL.TABLE_COLUMN_ORDER_SIZES", [{ values: [JSON.stringify(ResultsView.tablePreferences)] }]);

						$(parentElement).parents('.ot-table-content').find('[name="' + parentName + '"]').css({ 'width': width });

						self.fixTableHeaderAndRowsWidth();
					}
				});
				columnNode.appendChild(columnResizer);
			}

			columnNode.classList.add("ot-table-heading");
		}

		tableDiv.appendChild(headerElement);
		tableDiv.setAttribute("ot-header-template", headerTemplate);

		//	Add a rows content area:
		var rowsElements = document.createElement('div');
		rowsElements.classList.add("ot-table-allrows");
		tableDiv.appendChild(rowsElements);

		callback(content);

		var otTableHeader = self.contentArea().find(".ot-table-header");

		this.rowLocation().bind("slimscrolling.table", function (event, position, direction) {
			if (direction === "left")
				otTableHeader.css("transform", "translateX(-" + position + "px)");
		});
		//change table height if current view is from product workspace and update footer position
		if (!OTDCPWMarkForAssignmentView.dialogProperties.showSelectProductsArea) {
			$('.ot-modal-dialog-footer').css("bottom", 0);
			$('.ot-workflowjobs-wrapper').css("height", 'calc(100% - 11.7rem)');
		}
	}

	function parseStyle(style) {
		if (!style)
			return style;

		if (style in styleCache)
			return styleCache[style];

		var parts = style.split(";");
		var reworked = parts.map(function (part) {
			if (part.indexOf("flex:") == 0)
				part = part.replace(/^flex/, otui.Browser.flexAttribute);

			return part;
		});

		styleCache[style] = reworked.join(";");
		return styleCache[style];
	}

	var _load = function _load(location) {
		var self = this;
		self.blockContent();
		_get(self, function (response) {
			otui.empty(self.rowLocation().get(0));
			var results = $($.map(response, function (entry) { return self.addRowFromTemplate(entry); }));
			var rowLocation = self.rowLocation();
			rowLocation.append(results);
			self.unblockContent();
		});
	}

	var _get = function _get(self, callback) {
		var service = self.storedProperty("service");
		var data = self.storedProperty("serviceData");
		otui.services[service].read(data, callback, self);
	}

	//function to create global PIM position dropdown
	function createGlobalPimPositionDropdown() {
		var defaultLookupTable = OTDCPWWorkspaceManager.config.pim.mediaAttr.defaultLookupTable;
		var singleSelectedPIMPositions = OTDCPWWorkspaceManager.config.markForAssign.singleSelectablePIMPosition.split(',');
		var selectEl = renderComboEdit([], { domain_id: defaultLookupTable }, singleSelectedPIMPositions, true);
		selectEl.children[0].children[0].classList.add('otdcpw-maxWidth100');
		$('.otdcpw-markforassign-global-pimposition-dropdown').append(selectEl);
	}

	function readAssets(data, callback, view) {
		OTDCPWMarkForAssignmentView.selectedDomainValues = [];
		var resultData = [];
		resultData = OTDCPWMarkForAssignmentView.dialogProperties.resourceList;
		callback(resultData);
		var workflowContentArea = view.contentArea();
		//Fallback to default look table for reading media attributes if not able to read from asset
		var defaultLookupTable = OTDCPWWorkspaceManager.config.pim.mediaAttr.defaultLookupTable;
		var mediaAttrFldCriteria = OTDCPWAssetActionsManager.parseMetadataFldCriteria(
			OTDCPWWorkspaceManager.config.pim.mediaAttr.fieldId);
		for (var i = 0; i < resultData.length; i++) {
			//Set thumbnail
			var asset = resultData[i];
			var rowObject = workflowContentArea.find('ot-resource[resourceid="' + asset.asset_id + '"]');
			var thumbnailWrapper = rowObject.find(".ot-workflowjob-thumbnails");
			var thumbnailUrl = otui.service + "/assets/" + asset.asset_id + "/contents?rendition_type=thumbnail";
			var thumbnailEntry = view.getTemplate("thumbnailEntry");
			thumbnailEntry.querySelector(".ot-workflowjob-thumbnail-entry").setAttribute("src", thumbnailUrl);
			thumbnailWrapper[0].appendChild(thumbnailEntry);

			//Find PIM Media attribute in asset
			var fgs = asset.metadata && asset.metadata.metadata_element_list;
			var fg = fgs && fgs.find(function (element) { return element.id == mediaAttrFldCriteria.grpId });
			if (fg) {
				var media = fg.metadata_element_list.find(function (element) { return element.id == mediaAttrFldCriteria.id });
			}
			//Render combo element
			var value, fieldInfo = {};
			if (media && media.type == OTDCPWWorkspaceManager.METADATA_TABULAR && media.metadata_element_list.length == 1) {
				value = media.metadata_element_list[0].values;
				fieldInfo.domain_id = media.metadata_element_list[0].domain_id;
				fieldInfo.id = media.id;
			}
			//If no media field, then give default PIM attributes
			if (!media) {
				value = [];
				fieldInfo.domain_id = defaultLookupTable;
				fieldInfo.id = mediaAttrFldCriteria.id;
			}
			//Store the default media positions of each asset
			var valueEntryList = [];
			for (var j = 0; j < value.length; j++) {

				var id = value[j].value.field_value.value;
				valueEntryList.push(id);
			}
			var sValue = valueEntryList.length ? valueEntryList.sort().toString() : "";
			view.properties.assetMediaMap.set(asset.asset_id, sValue);
			var selectEl = renderComboEdit(value, fieldInfo, [], false);
			var div = rowObject.find(".otdcpw-pim-position");
			div[0].appendChild(selectEl);
		}
	}

	var renderChiclet = function renderChiclet(valueEntryList, editable, isTagField, selectEl) {
		var self = this;
		if (!(valueEntryList && valueEntryList.length)) {
			return;
		}
		var id = (valueEntryList.length > 0) && valueEntryList[0] && valueEntryList[0].value;
		var displayValue = (valueEntryList.length > 0) && valueEntryList[0] && valueEntryList[0].displayValue;
		displayValue = displayValue || id || '';
		var chicletElExists = this.querySelector('ot-chiclet[ot-id="' + CSS.escape(id) + '"]');

		if (chicletElExists)
			return;

		var chicletEl = document.createElement("ot-chiclet");
		chicletEl.setAttribute('displayValue', displayValue);
		chicletEl.id = id;
		chicletEl.value = JSON.stringify({ 'value': displayValue });
		chicletEl.readOnly = !editable;
		// commenting the code for now
		/*if (displayValue && !editable) {
			//Register on click event if view mode
			chicletEl.addEventListener("click", function() {
				var searchText = this.getAttribute('displayValue');
				if (searchText)
					SearchManager.performKeywordSearch(searchText);
			});
		}*/
		//Register on click for chiclet - Perfome remove operation
		chicletEl.addEventListener("click", function (event) {
			var optionEl = JSON.parse(event.currentTarget.value);
			var valueEntry = {
				"value": event.currentTarget.id,
				"displayValue": optionEl.value
			};
			var chicletElExists = self.querySelector('ot-chiclet[ot-id="' + CSS.escape(valueEntry.value) + '"]');
			var metadataEl = otui.parent(chicletEl, 'div');
			if (chicletElExists) {
				otui.remove(chicletElExists);
			}
			//Update the selected values in combo box
			xtag.fireEvent(metadataEl, "itemRemoved", { "detail": { "id": event.currentTarget.id } });
		})
		this.appendChild(chicletEl);
	};

	//variable to stor globally selected PIM positions
	OTDCPWMarkForAssignmentView.selectedDomainValues = [];

	var renderComboEdit = function renderComboEdit(value, fieldInfo, singleSelectedPIMPositions, globalPIMPosition) {

		var templateFrag = otui.Templates.get("ot-tabular-single-combo-templ");
		var div = document.createElement("div");
		div.setAttribute("class", "otdcpw-detail-col-div")
		var selectEl = templateFrag.querySelector('ot-select');
		if (otui.is(otui.modes.TABLET)) {
			selectEl.hintText = otui.tr('Select an option');
		}
		else {
			selectEl.hintText = otui.tr('Select or search for an option');
		}
		//If we set the following then, selected values will be shown as the placeholder 
		selectEl.displaySelectedItem = false;
		selectEl._querySelector(".ot-selector-hover-header-selectall").onclick = function () {
			selectEl.selectAllOptions.call(selectEl);
		};
		selectEl._querySelector(".ot-selector-hover-header-deselectall").onclick = function () {
			selectEl.deselectAllOptions.call(selectEl);
		};
		//Register change event for select element
		selectEl.addEventListener("change", function (event) {
			if (event && event.detail) {
				var optionEl = event.detail.option;
				var chicletSection = div.querySelector('.ot-tabular-chiclets-section');
				if (!chicletSection) {
					chicletSection = templateFrag.querySelector('.ot-tabular-chiclets-section');
				}
				var valueEntry = {
					"value": optionEl.getAttribute('value'),
					"displayValue": optionEl.getAttribute('title')
				};
				var valueEntryList = [];
				valueEntryList.push(valueEntry);
				if (event.detail.isSelected) {
					// option is being selected
					renderChiclet.call(chicletSection, valueEntryList, true, selectEl);
					selectEl.selectOptions.call(selectEl, [event.detail.id]);
				} else {
					var chicletElExists = chicletSection.querySelector('ot-chiclet[ot-id="' + CSS.escape(valueEntry.value) + '"]');
					if (chicletElExists) {
						otui.remove(chicletElExists);
					}
				}
				selectEl.dirty = true;

				if (globalPIMPosition) {
					//Remove error message under dropdown upon selection
					var errorSection = div.querySelector('.otdcpw-error-msg');
					if (errorSection) {
						errorSection.remove();
					}

					//add and remove globally selected PIM positions from selectedDomainValues variable
					let index = OTDCPWMarkForAssignmentView.selectedDomainValues.indexOf(optionEl.getAttribute('value'));
					if (event.detail.isSelected && index === -1) {
						OTDCPWMarkForAssignmentView.selectedDomainValues.push(optionEl.getAttribute('value'));
					} else if (index !== -1) {
						OTDCPWMarkForAssignmentView.selectedDomainValues.splice(index, 1);
					}
				} else {
					var errorSection1 = div.parentNode.querySelector('.otdcpw-error-msg');
					if (errorSection1) {
						errorSection1.style.display = 'none';
					}
				}
			}
		});
		//Fill chicklets
		var chicletSection = templateFrag.querySelector('.ot-tabular-chiclets-section');
		var editable = true;
		for (var i = 0; i < value.length; i++) {
			var valueEntryList = [];

			var id = value[i].value.field_value.value;
			var displayValue = value[i].value.display_value;
			displayValue = displayValue || id || '';

			valueEntryList.push({ "value": id, "displayValue": displayValue });
			renderChiclet.call(chicletSection, valueEntryList, editable);
		}

		if(fieldInfo.domain_id === undefined){
			fieldInfo.domain_id = OTDCPWWorkspaceManager.config.pim.mediaAttr.defaultLookupTable;
		}

		otui.services.selectLookup.read({ 'lookup': fieldInfo.domain_id, 'asSelect': false }, function (domainValues) {
			createOptions(selectEl, div, value, domainValues, singleSelectedPIMPositions, globalPIMPosition);
		});

		//Start observing the height of global chicklets section div to adjust the table height
		if (globalPIMPosition) {
			chickletObserver.observe(selectEl.nextElementSibling);
		}

		div.appendChild(templateFrag);
		return div;
	}

	const chickletObserver = new ResizeObserver(entries => {
		// this will get called whenever global chicklet section height changes
		entries.forEach(entry => {
			if (!OTDCPWMarkForAssignmentView.dialogProperties.showSelectProductsArea) {
				$('.ot-workflowjobs-wrapper').css("height", `calc(100% - 11.7rem - ${entry.contentRect.height}px)`);
			} else {
				$('.ot-workflowjobs-wrapper').css("height", `calc(100% - 18rem - ${entry.contentRect.height}px)`);
			}
		});
	});

	function createOptions(selectEl, div, value, domainValues, singleSelectedPIMPositions, globalPIMPosition) {
		var optionsFrag = document.createDocumentFragment();
		for (var i = 0; i < domainValues.length; i++) {
			//globalPIMPosition is false when options are generated at asset level and is true when generating options for Global PIM position dropdown 
			if (!globalPIMPosition || singleSelectedPIMPositions.indexOf(domainValues[i].value) === -1) {
				var optionEl = document.createElement("option");
				optionEl.value = domainValues[i].value;
				optionEl.text = domainValues[i].displayValue;
				optionEl.setAttribute('type', domainValues[i].type);
				optionsFrag.appendChild(optionEl);
			}
		}
		selectEl.appendChild(optionsFrag);
		// listening to item removed event
		div.addEventListener("itemRemoved", function (event) {
			if (event && event.detail && event.detail.id) {
				// De-selecting the option from combo box
				var optionEl = selectEl.store.hover.querySelector("ot-option[value='" + CSS.escape(event.detail.id) + "']");
				selectEl.deselectOption.call(selectEl, optionEl);
			}
		});

		//set the selected values 
		var selectedValue = [];
		var valueEntry;
		for (var i = 0; i < value.length; i++) {
			valueEntry = value[i];
			selectedValue.push(valueEntry.value.field_value.value);
		}
		selectEl.value = selectedValue;
	}

	otui.registerService('markforassignment', { 'read': readAssets });

})(window);
