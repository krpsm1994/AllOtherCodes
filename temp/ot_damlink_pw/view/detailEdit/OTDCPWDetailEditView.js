(function(exports) {	
	//Product Workspace Detail View - Metadata screen
	var OTDCPWDetailMetadataEdit = exports.OTDCPWDetailMetadataEdit =
		otui.define("OTDCPWDetailMetadataEdit",["withContent"],
			function(){
				this.properties = {
					'name' : 'OTDCPWDetailMetadataEdit', // Name of the view
					'title': otui.tr("Metadata"),
					'type': 'OTDCPWDetailMetadataEdit',
					'loaded': false
				};
				this._initContent = function _initContent(self, callback) {
					self.blockContent();
					var template = self.getTemplate("content");
					var content = $(otui.fragmentContent(template));
					callback(content);
					if(this.properties.assetInfo){
						loadMetadataContent.call(this,this.internalProperties.parentView,this.properties.assetInfo);
					}
				};
				this.bind("attached-to-view", function() {
					var parentView = this.internalProperties.parentView;
					
					if (parentView)
						parentView.bind("got-asset", loadMetadataContent.bind(this));
				});
				this.displayError = function displayError(validationMessage) {
					validationMessage = validationMessage || otui.tr("Please correct the errors indicated in red");
					var notification = {'message' : validationMessage, 'status' : 'warning'};
					otui.NotificationManager.showNotification(notification);
				};
				/**Validate if required metadata is entered */
				this.validate = function validate(){
					var result = {'valid':true};
					var contentArea = this.contentArea()[0];	
					//Below div gives only active FG metadata as right view
					var div = contentArea.querySelector(".otdcpw-right-view");
					Array.prototype.forEach.call(div.querySelectorAll(".otdcpw-detail-grid-column .otdcpw-detail-col-div"), function(el)
						{
							var inputEl = el.children[0];
							if(!inputEl){
								return;
							}
							inputEl.classList.remove("otdcpw-in-error");
							//Remove error div if already present
							var errorEl = el.querySelector(".otdcpw-error-msg");
							if(errorEl){
								errorEl.remove()
							}
							if(inputEl.dirty || ( inputEl.children[0] && inputEl.children[0].dirty ) ){
								if(inputEl.hasAttribute("otdcpw-required") && !inputEl.value){
									inputEl.classList.add("otdcpw-in-error");
									var divEl = document.createElement("div");
									divEl.setAttribute("class","otdcpw-error-msg");
									divEl.textContent = otui.tr("This field is required.");
									el.append(divEl);
									result.valid = false;
								}
							}
						}
					);
					var activeFg = $(".otdcpw-detail-side-div-active")[0].id;
					var fgList = this.properties.assetInfo.metadata.metadata_element_list;
					for(var i=0; i<fgList.length; i++){
						if(fgList[i].id == activeFg){
							continue;
						}
						//Go through each loaded FG, other than active FG and see if any data is dirty
						var fgFragment = this.properties.loadedFG.find(function(element){return element.id == fgList[i].id});
						if(fgFragment){
							Array.prototype.forEach.call(fgFragment.el[0].querySelectorAll(".otdcpw-detail-grid-column .otdcpw-detail-col-div"), function(el){
								var inputEl = el.children[0];
								if(!inputEl){
									return;
								}
								inputEl.classList.remove("otdcpw-in-error");
								var errorEl = el.querySelector(".otdcpw-error-msg");
								if(errorEl){
									errorEl.remove()
								}
								if(inputEl && inputEl.dirty || ( inputEl.children[0] && inputEl.children[0].dirty ) )
									if(inputEl.hasAttribute("otdcpw-required") && !inputEl.value){
										inputEl.classList.add("otdcpw-in-error");
										var divEl = document.createElement("div");
										divEl.setAttribute("class","otdcpw-error-msg");
										divEl.textContent = otui.tr("This field is required.");
										el.append(divEl);
										result.valid = false;
									}
							});
						}
					}
					return result.valid;
				}
				this.gatherMetadata = function gatherMetadata() {
					var metadata = [];
					var contentArea = this.contentArea()[0];			
					//Below div gives only active FG metadata as right view
					var div = contentArea.querySelector(".otdcpw-right-view");
					Array.prototype.forEach.call(div.querySelectorAll(".otdcpw-detail-grid-column .otdcpw-detail-col-div"), function(el)
						{
							var inputEl = el.children[0];
							if(!inputEl){
								return;
							}
							if(inputEl.dirty || ( inputEl.children[0] && inputEl.children[0].dirty ) )
								generateValueforSave(el,metadata);
						}
					);
					var activeFg = $(".otdcpw-detail-side-div-active")[0].id;
					var fgList = this.properties.assetInfo.metadata.metadata_element_list;
					for(var i=0; i<fgList.length; i++){
						if(fgList[i].id == activeFg){
							continue;
						}
						//Go through each loaded FG and see if any data is dirty
						var fgFragment = this.properties.loadedFG.find(function(element){return element.id == fgList[i].id});
						if(fgFragment){
							Array.prototype.forEach.call(fgFragment.el[0].querySelectorAll(".otdcpw-detail-grid-column .otdcpw-detail-col-div"), function(el){
								var inputEl = el.children[0];
								if(!inputEl){
									return;
								}
								if(inputEl && inputEl.dirty || ( inputEl.children[0] && inputEl.children[0].dirty ) )
									generateValueforSave(el,metadata);
							});
						}
					}
					return metadata.length ? {'metadata' : metadata} : {};
				}
			} );
	
	function generateValueforSave(el, metadataList, id){
		var id = id || el.parentElement.id;
		var fieldInfo = otui.MetadataModelManager.rationaliseFieldObject({'id' : id})
		
		var type;
		if(fieldInfo.type == OTDCPWWorkspaceManager.METADATA_TABULAR){
			type = "table";
		}else{
			type = getDataTypeMap(fieldInfo.data_type);
		}
		
		if(type!="table"){				
			metadataList.push({'id' : id, "type": "com.artesia.metadata.MetadataField", "value": generateSaveValue(id, type, el.children[0].value)});
		}else{			
			var values = [];
			Array.prototype.forEach.call(el.querySelectorAll("ot-chiclet"), function(el){
				var value = JSON.parse(el.value);
				values.push({"id" : el.id, "displayValue" : el.getAttribute("displayValue"), "value" : value.value});
			});
			var columns = fieldInfo.metadata_element_list;
			
			columns.forEach(function(col)
			{
				var listOfValues = [];

				values.forEach(function(val)
				{	
					//TODO sometimes displayvalue is also passed
					listOfValues.push(generateSaveValue(col.id, col.type, val.id, col.editType));
				});

				metadataList.push({'id' : col.id, 'type' : 'com.artesia.metadata.MetadataTableField', 'values' : listOfValues});
			});
				
		}
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
			value = value + 'T00:00:00Z';
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
					}
					else if((editType === 'cascade') || (editType === 'cascadetypeahead'))
					{
						obj = {"value" : { "field_value": { "type": type, "value": value } } };
						obj.value.type = "com.artesia.metadata.CascadingDomainValue";
						obj.value.displayValue = displayValue;
					}
				}
			}
		return obj;
	}
	function loadMetadataContent(parent,asset){
		this.unblockContent();
		//Check if tab selected is Metadata
		if(parent.properties.OTDCPWDetailEditView_selected != "OTDCPWDetailMetadataEdit" || this.properties.loaded){
			return;
		}
		//Set loaded
		this.properties.loaded = true;
		this.properties.loadedFG = []; //to store already loaded fieldgroups
		
		//if active field group info exists, then preselect the same else the first field group
		var fgID = this.properties.assetInfo.metadata.metadata_element_list[0].id;
		OTDCPWDetailMetadataEdit.loadSidebar.call(this);	
		var fgInfo = getFGInfo.call(this,fgID);
		var fgMetadata = getFgMetadata.call(this,fgID);
		OTDCPWDetailMetadataEdit.fillMetadata.call(this,fgInfo,fgMetadata);	
		//Display in edit mode
		if(OTDCPWDetailEditView.editMode){
			OTDCPWDetailMetadataEdit.handleEdit.call(this);
		}
	}
	OTDCPWDetailMetadataEdit.loadSidebar = function loadSidebar(){
		var fgList = this.properties.assetInfo.metadata.metadata_element_list;
		$(".otdcpw-left-nav").empty();
		for(var i=0; i<fgList.length; i++){
			var localizedFieldGroupName = otui.MetadataModelManager.getCategoryDisplayName(fgList[i].id);
			var navEl = getNavOption(fgList[i].id, localizedFieldGroupName?localizedFieldGroupName:fgList[i].name, 
				OTDCPWDetailMetadataEdit.handleOnClick)
			if(i==0){
				navEl.setAttribute("class","otdcpw-detail-side-div otdcpw-detail-side-div-active");
			}
			$(".otdcpw-left-nav").append(navEl);
		}
	}
	var getNavOption = function getNavOption(id,val,handler){
		var spanEl = document.createElement("span");
		if(val)
			spanEl.textContent = val;
		var div = document.createElement("div");
		div.setAttribute("class","otdcpw-detail-side-div")
		div.setAttribute("id",id);
		div.addEventListener("click",handler);
		div.append(spanEl);
		return div;
	}
	OTDCPWDetailMetadataEdit.handleOnClick = function handleOnClick(event){
		var oldActiveEl = $(".otdcpw-detail-side-div-active");
		oldActiveEl.removeClass("otdcpw-detail-side-div-active");
		event.currentTarget.setAttribute("class","otdcpw-detail-side-div otdcpw-detail-side-div-active");
		OTDCPWDetailMetadataEdit.loadMetadata(event,oldActiveEl);
	}
    OTDCPWDetailMetadataEdit.loadMetadata = function loadMetadata(event,oldActiveEl){
		var id = event.currentTarget.id;
		var view = otui.Views.containing(event.currentTarget);
		var fgMetadata = getFgMetadata.call(view,id);
		var fgInfo = getFGInfo.call(view,id);		
        var elfound = false;	
		var el = $(".otdcpw-right-view").detach();//Detach DOM
		//Detach and store the FG loaded Metadata
		for( var i=0; i<view.properties.loadedFG.length; i++){
			if(view.properties.loadedFG[i].id == oldActiveEl[0].id){
				view.properties.loadedFG[i].el = el;
				elfound = true;
				break;
			}
		}
		if (!elfound) {
			view.properties.loadedFG.push({ id: oldActiveEl[0].id, el: el });
		}
		if (id === 'OTDC.PW.FG.VARIANTS') {
			// Extract the variants array
			$('#otdcpwVariantsTable').remove();
			$(".otdcpw-body").append(OTDCUtilities.generateVariantsTable(fgMetadata));
		} else {
			$('#otdcpwVariantsTable').remove();
			//Load the FG Metadata if found in stored	
			//Check if this FG is already loaded, if yes load the same 
			for (var i = 0; i < view.properties.loadedFG.length; i++) {
				if (view.properties.loadedFG[i].id == id) {
					$(".otdcpw-body").append(view.properties.loadedFG[i].el);
					return;
				}
			}
			OTDCPWDetailMetadataEdit.fillEditMetadata(fgInfo, fgMetadata);
		}
	}
	OTDCPWDetailMetadataEdit.fillMetadata = function fillMetadata(fgInfo,metadata){		
		var divEl = document.createElement("div");
		divEl.setAttribute("class","otdcpw-right-view");
		var rowEl = getRowEl();
		for(var i=0; i< metadata.length; i++){
			//Get Field info
			var fieldInfo = fgInfo.find(function(element){return element.id == metadata[i].id});
			if(metadata[i].type == OTDCPWWorkspaceManager.METADATA_TABULAR ){
				fieldInfo = fieldInfo.metadata_element_list;
				var colEl = handleTabularMetadata(metadata[i]);
				if(metadata[i].metadata_element_list.length != 1){
					var isTable = true;
				}
			}else{
				var colEl = getColumnEl(metadata[i].id);
				var labelEl = getLabelEl(otui.MetadataModelManager.getDisplayName(metadata[i].id), fieldInfo.required);
				
				if(metadata[i].value && metadata[i].value.value)
					var labelVal = getSpanEl(metadata[i].value.value.display_value || metadata[i].value.value.value);
				else
					var labelVal = getSpanEl();
				colEl.append(labelEl,labelVal);
			}
			//If not a singular column table, take the complete row width
			if(isTable){
				colEl.style.width = "100%";
				if(i % 2 != 0){
					divEl.append(rowEl);
					var rowEl = getRowEl();
					rowEl.append(colEl);
					divEl.append(rowEl);
					var rowEl = getRowEl();
				}else{
					rowEl.append(colEl);
					divEl.append(rowEl);
					var rowEl = getRowEl();
				}
				continue;
			}else{
				rowEl.append(colEl);
			}
			if( i % 2 != 0 || i + 1 == metadata.length){
				divEl.append(rowEl);
				var rowEl = getRowEl();
			}
		}
		$(".otdcpw-body").append(divEl);
	}
	var getFgMetadata = function getFgMetadata(id){
		var filteredFgData = [];
		var assetMetadata = this.properties.assetInfo.metadata.metadata_element_list;
		for(var i=0; i<assetMetadata.length; i++){
			if(assetMetadata[i].id == id){
				var fgMetadata = this.properties.assetInfo.metadata.metadata_element_list[i].metadata_element_list;
				break;
			}
		}
		//Filter out non displayable fields
		var fgInfo = getFGInfo.call(this,id);
		for(var i=0; i<fgMetadata.length; i++){
			var fieldInfo = fgInfo.find(function(element){return element.id == fgMetadata[i].id});
			if(fgMetadata[i].type == OTDCPWWorkspaceManager.METADATA_TABULAR && fieldInfo.metadata_element_list[0].displayable){
				filteredFgData.push(fgMetadata[i]);
			}
			else{
				if(fieldInfo.displayable){
					filteredFgData.push(fgMetadata[i]);
				}
			}
		}
		return filteredFgData;
	}
	var handleTabularMetadata = function handleTabularMetadata(metadataObj){
		var colEl = getColumnEl(metadataObj.id);
		var labelEl = getLabelEl(otui.MetadataModelManager.getDisplayName(metadataObj.id));
		colEl.append(labelEl);
		//If singular column table, display values as buttons
		if(metadataObj.metadata_element_list.length == 1){
			var div = document.createElement("div");
			div.setAttribute("class","otdcpw-detail-col-div");
			var tabValues = metadataObj.metadata_element_list[0].values;
			for(var i=0; i<tabValues.length; i++){
				/*Create Button*/
				var buttonEl = document.createElement("button");
				if(tabValues[i].value){
					buttonEl.textContent = tabValues[i].value.value || tabValues[i].value.display_value || tabValues[i].value.field_value.value;
				}
				buttonEl.setAttribute('type', "button");			
				buttonEl.setAttribute('class',"otdcpw-detail-tabular-button");
				buttonEl.setAttribute('disabled',true);						
				div.append(buttonEl);				
			}
			colEl.append(div);
		}
		//Else display like normal table
		else{
			var tableEl = document.createElement("table");
			tableEl.setAttribute("class","otdcpw-detail-table");
			var tableElHead = document.createElement("thead");
			tableElHead.setAttribute("class","otdcpw-detail-theader");
			var tableElbody = document.createElement("tbody");
			var tableElthRow = document.createElement("tr")
			tableElthRow.setAttribute("class","otdcpw-detail-trow");
			for(var i=0; i<metadataObj.metadata_element_list[0].values.length; i++){
				var tableElRow = document.createElement("tr");
				tableElRow.setAttribute("class","otdcpw-detail-trow");
				for(var j=0; j<metadataObj.metadata_element_list.length; j++){
					if(i==0){
						var tableElth = document.createElement("th");
						tableElth.setAttribute("class","otdcpw-detail-th");
						tableElth.style.width = 100 / metadataObj.metadata_element_list.length + "%";
						tableElth.innerText = metadataObj.metadata_element_list[j].name;
						tableElthRow.append(tableElth);
					}
					var tableElData = document.createElement("td");
					tableElData.setAttribute("class","otdcpw-detail-td")
					tableElData.style.width = 100 / metadataObj.metadata_element_list.length + "%";
					if(metadataObj.metadata_element_list[j].values[i].value && metadataObj.metadata_element_list[j].values[i].value.value){
						tableElData.innerText = metadataObj.metadata_element_list[j].values[i].value.value
					}
					tableElRow.append(tableElData);
				}
				
				tableElbody.append(tableElRow);
			}
			tableElHead.append(tableElthRow);
			tableEl.append(tableElHead);
			tableEl.append(tableElbody);
			colEl.append(tableEl);
		}
		return colEl;
	}
	var getColumnEl = function getColumnEl(id){
		var colEl = document.createElement("div");
		colEl.setAttribute("class","otdcpw-detail-grid-column");
		if(id){
			colEl.setAttribute("id",id);
		}
		return colEl;
	}
	var getRowEl = function getRowEl(){
		var rowEl = document.createElement("div");
		rowEl.setAttribute("class","otdcpw-detail-grid-row");
		return rowEl;
	}
	var getLabelEl = function getLabelEl(value, isMandatory){
		var labelEl = document.createElement("label");
		labelEl.setAttribute("class","otdcpw-detail-col-label");
		labelEl.innerText = value
		if(isMandatory){
			var spanEl = document.createElement("span");
			spanEl.textContent = "*";
			spanEl.setAttribute("class","otdcpw-mandatory-label");
			labelEl.append(spanEl);
		}
		return labelEl;
	}
	var getSpanEl = function getSpanEl(value){
		var spanEl = document.createElement("span");
		if(value)
			spanEl.textContent = value;
		var div = document.createElement("div");
		div.setAttribute("class","otdcpw-detail-col-div");
		div.append(spanEl);
		return div;
	}

	var getInputEl = function getInputEl(value,length,editable,isMandatory,type){
		var inputEl = document.createElement("input");
		if(type){
			inputEl.setAttribute("type","date")
		}else{
			inputEl.setAttribute("type","text")
		}
		inputEl.setAttribute("class","otdcpw-detail-input");
		inputEl.setAttribute("maxlength",length);
		inputEl.setAttribute("onchange","OTDCPWDetailMetadataEdit.setDirty(event)");
		if(!editable){
			inputEl.disabled = true;
		}
		//If required, mark as required
		if(isMandatory){
			inputEl.setAttribute("otdcpw-required","");
		}
		if(value)
			inputEl.value = value;
		var div = document.createElement("div");
		div.setAttribute("class","otdcpw-detail-col-div")
		div.append(inputEl);
		return div;
	}

	OTDCPWDetailMetadataEdit.setDirty = function setDirty(event){
		event.currentTarget.dirty = true;
	}
	var getTextareaEl = function getTextareaEl(value,length,editable){
		var textareaEl = document.createElement("textarea");
		textareaEl.setAttribute("class","otdcpw-detail-textarea");
		textareaEl.setAttribute("maxlength",length);
		textareaEl.setAttribute("onchange","OTDCPWDetailMetadataEdit.setDirty(event)");
		if(value)
			textareaEl.value = value;
		if(!editable){
			textareaEl.disabled = true;
		}
		var div = document.createElement("div");
		div.setAttribute("class","otdcpw-detail-col-div")
		div.append(textareaEl);
		return div;
	}

	OTDCPWDetailMetadataEdit.handleEdit = function handleEdit(event){
		// //Get Active field group
		var activeFG = $(".otdcpw-detail-side-div-active").attr('id');
		var el = $(".otdcpw-right-view").detach();//Detach DOM
		var fgInfo = getFGInfo.call(this,activeFG);	
		var fgMetadata = getFgMetadata.call(this,activeFG);
		OTDCPWDetailMetadataEdit.fillEditMetadata(fgInfo,fgMetadata);
	}
	
	var getFGInfo = function getFGInfo(id){
		var fgsInfo = this.properties.modelInfo.metadata_element_list;
		for(var i=0; i<fgsInfo.length; i++){
			if(fgsInfo[i].id == id){
				var fgInfo = fgsInfo[i].metadata_element_list;
				break;
			}
		}
		return fgInfo;		
	}

	var getComboEl = function getComboEl(value,fieldInfo){
		var el;
		if(fieldInfo.editable){
			el = renderComboEdit(value,fieldInfo);
		}else{
			el = renderComboReadOnly(value,fieldInfo);
		}
		return el;
	}

	var getSimpleCombo = function getSimpleCombo(value,field_value,fieldInfo){
		var includeBlank = true;
		var selectEl = document.createElement("ot-select");
		var value = field_value;		
		selectEl.addEventListener("change",function(event){ 
											selectEl.dirty = true;
								});
		otui.services.selectLookup.read({'lookup' : fieldInfo.domain_id, 'asSelect' : includeBlank}, function(domainValues){			
			for (var i = 0; i < domainValues.length; i++)
			{
				var optionE = document.createElement("option");
				optionE.value = domainValues[i].value;
				optionE.text = domainValues[i].displayValue;
				optionE.setAttribute('type', domainValues[i].type);
				selectEl.appendChild(optionE);
			}
			if(value){
				selectEl.value = value;
				selectEl.originalValue = selectEl.value;
			}
		});
		if(!fieldInfo.editable){
			selectEl.setAttribute("disabled","");
		}
		var div = document.createElement("div");
		div.setAttribute("class","otdcpw-detail-col-div")
		div.append(selectEl);
		return div;
	}
	var renderComboReadOnly = function renderComboReadOnly(value,fieldInfo){
		var div = document.createElement("div");
		div.setAttribute("class","otdcpw-detail-col-div")
		var templateFrag = otui.Templates.get("ot-tabular-single-column-readonly-templ");	
		var chicletSection = templateFrag.querySelector('.ot-tabular-chiclets-section');
		var editable = fieldInfo.editable;
		for(var i=0;i<value.length; i++){
			var valueEntryList = [];
			
			var id =  value[i].value.field_value.value;
			var displayValue =  value[i].value.display_value;
			displayValue = displayValue || id || '';

			valueEntryList.push({"value": id, "displayValue": displayValue});
			renderChiclet.call(chicletSection, valueEntryList, editable);
		}
		div.appendChild(templateFrag);
		return div;
	}
	var renderComboEdit = function renderComboEdit(value,fieldInfo){
		
		var templateFrag = otui.Templates.get("ot-tabular-single-combo-templ");
		var div = document.createElement("div");
		div.setAttribute("class","otdcpw-detail-col-div")
		var selectEl = templateFrag.querySelector('ot-select');
		if(otui.is(otui.modes.TABLET))
		{
			selectEl.hintText = otui.tr('Select an option');
		}
		else
		{
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
		selectEl.addEventListener("change", function(event) {
			if(event && event.detail){
				var optionEl = event.detail.option;
				var chicletSection = div.querySelector('.ot-tabular-chiclets-section');
				if(!chicletSection)
				{
					chicletSection = templateFrag.querySelector('.ot-tabular-chiclets-section');
				}
				var valueEntry = {
					"value": optionEl.getAttribute('value'), 
					"displayValue": optionEl.getAttribute('title')
				};
				var valueEntryList = [];
				valueEntryList.push(valueEntry);
				if(event.detail.isSelected)
				{
					// option is being selected
					renderChiclet.call(chicletSection, valueEntryList, true, selectEl);
					selectEl.selectOptions.call(selectEl, [event.detail.id]);
				}else{
					var chicletElExists = chicletSection.querySelector('ot-chiclet[ot-id="' + CSS.escape(valueEntry.value) + '"]');
					if(chicletElExists)
					{
						otui.remove(chicletElExists);
					}
				}
				selectEl.dirty = true;
			}
		});
		//Fill chicklets
		var chicletSection = templateFrag.querySelector('.ot-tabular-chiclets-section');
		var editable = fieldInfo.editable;
		for(var i=0;i<value.length; i++){
			var valueEntryList = [];
			
			var id =  value[i].value.field_value.value;
			var displayValue =  value[i].value.display_value;
			displayValue = displayValue || id || '';

			valueEntryList.push({"value": id, "displayValue": displayValue});
			renderChiclet.call(chicletSection, valueEntryList, editable);
		}
		
		otui.services.selectLookup.read({'lookup' : fieldInfo.domain_id, 'asSelect' : false}, function(domainValues){
			var optionsFrag = document.createDocumentFragment();
			for (var i = 0; i < domainValues.length; i++)
			{
				var optionEl = document.createElement("option");
				optionEl.value = domainValues[i].value;
				optionEl.text = domainValues[i].displayValue;
				optionEl.setAttribute('type', domainValues[i].type);
				optionsFrag.appendChild(optionEl);
			}
			selectEl.appendChild(optionsFrag);
			// listening to item removed event
			div.addEventListener("itemRemoved", function(event){
				if(event && event.detail && event.detail.id)
				{
					// De-selecting the option from combo box
					var optionEl = selectEl.store.hover.querySelector("ot-option[value='" + CSS.escape(event.detail.id) +"']");
					selectEl.deselectOption.call(selectEl, optionEl);
				}
			});
	
			//set the selected values 
			var selectedValue = [];
			var valueEntry;			
			for(var i = 0; i < value.length; i++)
			{
				valueEntry = value[i];
				selectedValue.push(valueEntry.value.field_value.value);
			}
			selectEl.value = selectedValue;
		});		
	
		div.appendChild(templateFrag);
		return div;
	}
	var renderChiclet = function renderChiclet(valueEntryList, editable, isTagField, selectEl)
	{
		var self = this;
		if(!(valueEntryList && valueEntryList.length))
		{
			return;
		}
		var id = (valueEntryList.length > 0) && valueEntryList[0] && valueEntryList[0].value;
		var displayValue = (valueEntryList.length > 0) && valueEntryList[0] && valueEntryList[0].displayValue;
		displayValue = displayValue || id || '';
		var chicletElExists = this.querySelector('ot-chiclet[ot-id="' + CSS.escape(id) + '"]');
		
		if(chicletElExists)
			return;
		
		var chicletEl = document.createElement("ot-chiclet");
		chicletEl.setAttribute('displayValue', displayValue);		
		chicletEl.id = id;
		chicletEl.value = JSON.stringify({'value': displayValue});
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
		chicletEl.addEventListener("click", function(event) {
			var optionEl = JSON.parse(event.currentTarget.value);
			var valueEntry = {
				"value": event.currentTarget.id,
				"displayValue": optionEl.value
			};			
			var chicletElExists = self.querySelector('ot-chiclet[ot-id="' + CSS.escape(valueEntry.value) + '"]');
			var metadataEl = otui.parent(chicletEl, 'div');
			if(chicletElExists)
			{
				otui.remove(chicletElExists);
			}
			//Update the selected values in combo box
			xtag.fireEvent(metadataEl, "itemRemoved", {"detail": {"id" : event.currentTarget.id}});
		})
		this.appendChild(chicletEl);
	};
	var getEditEl = function getEditEl(value, fieldInfo, field_value){
		switch(fieldInfo.edit_type){
			case "SIMPLE":
				var element = getInputEl(value,fieldInfo.data_length, fieldInfo.editable,fieldInfo.required);
				return element;				
			case "TEXTAREA":
				var element = getTextareaEl(value,fieldInfo.data_length,fieldInfo.editable,fieldInfo.required);
				return element;				
			case "COMBO":
				if(fieldInfo.type == OTDCPWWorkspaceManager.METADATA_FIELD){
					var element = getSimpleCombo(value,field_value,fieldInfo);
					return element;	
				}else{
					var element = getComboEl(value,fieldInfo);				
					return element;
				}
			case "DATE":
				var date;
				date = value ? value.substring(0,10) : null;			
				var element = getInputEl(date,fieldInfo.data_length,fieldInfo.editable,fieldInfo.required,"date");
				return element;
			default:
				if(fieldInfo.id == "ARTESIA.FIELD.ASSET ID"){
					var element = getInputEl(value,fieldInfo.data_length, fieldInfo.editable,fieldInfo.required);
					return element;
				}else{
					var div = document.createElement("div");
					div.setAttribute("class","otdcpw-detail-col-div");
					return div;
				}
		}
	}

	OTDCPWDetailMetadataEdit.fillEditMetadata = function fillEditMetadata(fgInfo, metadata){
		var divEl = document.createElement("div");
		divEl.setAttribute("class","otdcpw-right-view");
		var rowEl = getRowEl();
		for(var i=0; i< metadata.length; i++){
			var field_value;
			//Get the field Info
			var fieldInfo = fgInfo.find(function(element){return element.id == metadata[i].id});
			var colEl;
			//Singular column table
			if(metadata[i].type == OTDCPWWorkspaceManager.METADATA_TABULAR && metadata[i].metadata_element_list.length == 1){	
				var value = metadata[i].metadata_element_list[0].values;
				fieldInfo = fieldInfo.metadata_element_list[0];
			} else if(metadata[i].type == OTDCPWWorkspaceManager.METADATA_TABULAR && metadata[i].metadata_element_list.length > 1){	
				var isTable = true;
				fieldInfo = fieldInfo.metadata_element_list;
			}
			
			if(isTable){
				colEl = handleTabularMetadata(metadata[i]);
				
				colEl.style.width = "100%";
				if(i % 2 != 0){
					divEl.append(rowEl);
					var rowEl = getRowEl();
					rowEl.append(colEl);
					divEl.append(rowEl);
					var rowEl = getRowEl();
				}else{
					rowEl.append(colEl);
					divEl.append(rowEl);
					var rowEl = getRowEl();
				}
				continue;
			} else {
				colEl = getColumnEl(metadata[i].id);
				if(metadata[i].value && metadata[i].value.value ){
					var value = metadata[i].value.value.value || metadata[i].value.value.display_value || metadata[i].value.value.field_value.value;
					field_value = metadata[i].value.value.field_value && metadata[i].value.value.field_value.value;
				}				
				var labelEl = getLabelEl(otui.MetadataModelManager.getDisplayName(metadata[i].id), fieldInfo.required);
				var labelVal = getEditEl(value, fieldInfo, field_value);
				colEl.append(labelEl,labelVal);
				rowEl.append(colEl);
				if( i % 2 != 0 || i + 1 == metadata.length){
					divEl.append(rowEl);
					var rowEl = getRowEl();
				}
				value = fieldInfo = null;
			}
						
			
		}
		$(".otdcpw-body").append(divEl);
	}

    otui.registerService('otdcpwAssetsEdit', {
    	'read' : function(data, callback, view){
			if(!view) view = otui.main.getChildView(OTDCPWDetailEditView);

			//returning if the user does not have OTDC.PW.VIEW FET.
			if((OTDCUtilities.isPWContext() && !otui.UserFETManager.isTokenAvailable("OTDC.PW.VIEW")) || 
				(!OTDCUtilities.isPWContext() && !otui.UserFETManager.isTokenAvailable("OTDCBW.VIEW"))) 
			{
				view.storedProperty("no-results-msg", otui.tr("You do not have permission to view assets."));
				view.contentArea()[0].querySelector('.ot-results-header').remove();
				callback();
				return;
			}
			var nodeID = data.nodeID;
			var pageID = nodeID;
			//Overriding sort field
			data.pageProperties.sortField = "NAME";
			PageManager.savePageData(pageID, data.pageProperties);
			var preferenceID = PageManager.getFieldPreferenceName();
			var extraFields = PageManager.getExtraFields();

			var sendResults = function sendResults(response, success)
			{
				view.unblockContent();
				if(!success){		
					callback(response, success, 0);
				}else{						
					PageManager.pageProperties[pageID] = data.pageProperties;
					let totalItems = view.properties.folderData?.container_child_counts?.total_child_count;
					callback(response, success, totalItems || 0);
					$('input[type="search"].uxa-text-input').attr('placeholder', otui.tr('Search within assets'));
					document.querySelectorAll('.uxa-multi-value-chip-label').forEach(function (label) {
						if (label.textContent.includes("Search for")) {
							label.closest('.uxa-multi-value-chip').style.display = 'none';
						}
					});
				}
			}

			if(data.appliedFacets)
    			appliedFacets = JSON.parse(data.appliedFacets);

			let facetConfigurationName = '';
			let defaultFacetName='';
			if(OTDCUtilities.isPWContext()){
				defaultFacetName = OTDCUtilities.defaultPWAssetFacetName;
				facetConfigurationName = (OTDCPWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
			} else{
				defaultFacetName = OTDCUtilities.defaultBWAssetFacetName;
				facetConfigurationName = (OTDCBWWorkspaceManager.config || {}).assetFacetConfig || defaultFacetName;
			}

    		var hasFacets = !!(appliedFacets.facets.length);
			if(hasFacets){
				if(!OTDCPWFacetsManager.currentFacetConfiguration) {
					OTDCPWFacetsManager.readFacets(facetConfigurationName, defaultFacetName, function(){
						OTDCPWAssetSearchManager.getFolderAssetsByKeyword("*", data.pageProperties.page, PageManager.assetsPerPage, preferenceID, extraFields, data, function(results, totalItems, success) {
							PageManager.numTotalChildren[pageID] = totalItems;
							sendResults(results, success);
						});
					});
				} else {
					OTDCPWAssetSearchManager.getFolderAssetsByKeyword("*", data.pageProperties.page, PageManager.assetsPerPage, preferenceID, extraFields, data, function(results, totalItems, success) {
						PageManager.numTotalChildren[pageID] = totalItems;
						sendResults(results, success);
					});
				}
			}else{
				//view.properties.searchID = "*";
				OTDCUtilities.doFolderChildrenRead(data, sendResults, view);
			} 
		}
    });

	var OTDCPWAssetsWrapperEdit = exports.OTDCPWAssetsWrapperEdit = otui.define("OTDCPWAssetsWrapperEdit" ,["withChildren"], function(){
		this.properties = {
			'name' : 'OTDCPWAssetsWrapperEdit', // Name of the view
			'title': otui.tr("Assets"),
			'type': 'OTDCPWAssetsWrapperEdit'
		}
		this._initContent = function _initContent(self, callback) {
			var template = self.getTemplate("content");				
			callback(template);
		}
	});

	var OTDCPWAssetsFolderEditView = exports.OTDCPWAssetsFolderEditView = otui.define("OTDCPWAssetsFolderEditView",function(){
		this.properties = {
			'name' : 'OTDCPWAssetsFolderEditView', // Name of the view
			'title': otui.tr("Asset Library"),
			'type': 'OTDCPWAssetsFolderEditView'
		}
		this._initContent = function _initContent(self, callback) {
			var template = self.getTemplate("content2");				
			callback(template);
			var parent = this.internalProperties.parentView.internalProperties.parentView.internalProperties.parentView;
		}
		this.bind("attached-to-view", function() {
			var detailView = this.internalProperties.parentView;
			if (detailView){
				detailView.bind("got-subfolderinfo", loadSubFolderArea.bind(this));
			}				
		});
		
		this.bind("show",function(){
			var parent = this.internalProperties.parentView.internalProperties.parentView.internalProperties.parentView;
			if(parent.internalProperties.childList && this.internalProperties.physicalContent){
				this.properties.loaded = false;
				loadSubFolderArea.call(this);
			}
		});
	});

	var OTDCPWAssetsSidebarEdit = exports.OTDCPWAssetsSidebarEdit = otui.define("OTDCPWAssetsSidebarEdit",["withAccordion"],function(){
		this.properties = {
			'name' : 'OTDCPWAssetsSidebarEdit', // Name of the view
			'title': otui.tr("Left panel"),
			'type': 'OTDCPWAssetsSidebarEdit',
			'open': [0],//Index of the view - here it means 0th view(of this.hierarchy) should be open by default
			'searchConfigID': "", 
			'nodeID': "",
			'appliedFacets': JSON.stringify({'facets': []})
		}		
			
		this._initContent = function _initContent(self, callback) {
			var template = self.getTemplate("content1");
			var content = $(template);

			if(self.properties.nodeID)
				self.getChildView(OTDCPWAssetsFacetsView).properties.needsUpdate = true;

			searchConfigList = SearchManager.searchConfigurations.response.search_configurations_resource.search_configuration_list;
			callback(content);
		};
		
		this.hierarchy =[{'view': OTDCPWAssetsFolderEditView},{'view' : OTDCPWAssetsFacetsView}];


		this.watch('nodeID', function(newVal, oldVal)
		{
			if(newVal != oldVal)
			{
				if(this.properties.open[0] == 0)
				{
					this.getChildView(OTDCPWAssetsFacetsView).properties.facetsRendered = false;
					this.getChildView(OTDCPWAssetsFacetsView).properties.needsUpdate = true;
				}
				else
					this.getChildView(OTDCPWAssetsFacetsView).updateFacets();
			}
		});
		
		this.updateFacets = function()
		{
			this.getChildView(OTDCPWAssetsFacetsView).properties.isViewRendered = false;
            this.getChildView(OTDCPWAssetsFacetsView).updateFacets(JSON.parse(this.properties.appliedFacets).facets);
		};
	});

    //Product Workspace Detail View - Assets screen
	var OTDCPWAssetsEditView = exports.OTDCPWAssetsEditView =
		otui.extend("ResultsView","OTDCPWAssetsEditView",
			function(){				
				var pageprop = { 'assetsPerPage': PageManager.assetsPerPage,
								'page': "0",
								'sortField': "NAME",
								'sortPrefix': "asc" };
				this.properties = {
					'name' : 'OTDCPWAssetsEditView', // Name of the view
					'title': otui.tr("Assets"),
					'type': 'OTDCPWAssetsEditView',
					'sourceName': 'OTDCPWAssetsEditView',
					'lazyLoad' : true,
					'selection_context' : null,
                    'service' : 'otdcpwAssetsEdit',
                    'serviceData' : {},
                    'templateName': PageManager.getResultsViewTemplate(),
                    'minCellWidth' : 320,
					'isMosaic': false,
					'pageProperties' : pageprop, 
                    'no-results-msg' : otui.tr("No Assets Found"),
					'loaded': false,
					'overridePageSizeTo':true,
					'searchConfigID': "none",
					'savedSearchID': "none",
					'appliedFacets': JSON.stringify({'facets': []})
				};
				
				this.idProperty = 'folderID';
				this.serviceDataMap = {'nodeID' : 'folderID', 'virtFolderId' : 'virtFolderId',
				'pageProperties' : 'pageProperties', 'appliedFacets' : 'appliedFacets', 
				'filterTerm' : 'filterTerm', 'searchConfigID' : 'searchConfigID', 
				'searchScopeID' : 'searchScopeID', 'metadataLocale' : 'metadataLocale', 
				'currentAccordion' : 'currentAccordion', 'advancedSearch' : 'advancedSearch', 
				'savedSearchID' : 'savedSearchID', 'searchID' : 'searchID', 'breadcrumb' : 'breadcrumb'};
       
				this._initContent = function _initContent(self, callback) {
					self.storedProperty("title", otui.tr("Assets"));
					self.storedProperty("sortOptions", RecentArtifactsManager.recentFoldersSortOptions || []);
					self.storedProperty("extraSortOptions", PageManager.searchSortOptions || []);
					self.storedProperty("newActionsLookupName", "OTDCPWDetailViewHeader");
					self.storedProperty("showRefresh", true); 
					self.storedProperty("showLocalSearch", true);
					//Set page properties
					this.properties.pageProperties = OTDCPWDetailView.getDefaultPageProperties();
					//This is used by Select All pages option
					this.selectionContext = {
						'type' : 'com.artesia.asset.selection.FolderSelectionContext',
						'map' : {'folder_id' : "folderID"},
						'alwaysUse' : false,
						'includeFolderDescendents' : true
					};							
					this.internalProperties.arguments = arguments;
					var self = this;
					if(this.internalProperties.parentView.internalProperties.parentView.internalProperties.childList){
						setTimeout(function(){
							loadAssetsResultsArea.call(self);
						}, 100);						
					}						
				};				
				this.bind("attached-to-view", function() {
					  this.unbind("detach");

					  this.bind("detach", function () {
					    try {
					      if (this.internalProperties && this.internalProperties.inAction) {
					        this.internalProperties.inAction = false;
					      }

					      // SAFE access to content area
					      const area = this.contentArea && this.contentArea();
					      const $area = area? (typeof area.find === "function" ? area : $(area)) : null;

					      if ($area && $area.length) {
					        const $results = $area.find(".ot-asset-results");
					        if ($results && $results.length) {
					          // Mirror the useful parts of the base cleanup, but null-safe
					          $results.off();
					          if ($results.is(":hidden")) $results.show();
					          const park = document.querySelector("#single-spa-container");
					          if (park) park.appendChild($results[0]);
					        }
					      }
					    } catch (err) {
					      console.warn("Custom detach: non-fatal error ignored", err);
					    }
					});
					
					var parentView = this.internalProperties.parentView;
					
					if (parentView)
						parentView.bind("got-subfolderinfo", loadwithdelay.bind(this));
				});
				this.idProperty = function()
				{
					//Changing this logic as pagination not working as searchmanager is returing different ID when facets are selected
					// return SearchManager.getSearchResultID(this, 'folderID', true);
					return this.properties.folderID;
				};
				this.watch("folderID",function(newVal,oldVal){
					if(newVal != oldVal){
						var parentView = this.internalProperties.parentView;
						parentView.properties.folderID = newVal;
						parentView.getChildView(OTDCPWAssetsSidebarEdit).properties.nodeID = newVal;
					}
				});
				this.bind("reload-data", function()
				{
					if(window['OTDCPWAssetsSidebarEdit']) {
						var view = this.internalProperties.parentView.getChildView(OTDCPWAssetsSidebarEdit);
						if(view)
						{
							//Update sidebar facets when view is reloaded, same as Homeview
							view.properties.appliedFacets = this.properties.appliedFacets;
							var facets = document.querySelector(".ot-facets-root .ot-facet");
							if(facets)
							{
								SearchManager.clearSearchForKeyword(this.properties.folderID);
								view.updateFacets();
							}
						}
					}
				});
				this.bind("single-spa-results-loaded", function () {
					let otResults = this.contentArea()[0].querySelector(".ot-asset-results");
					if (otResults) {
						let view = this;
						$(otResults).on("applied-facets-change", function (event) {
							let eventDetail = event.detail;
							if (eventDetail.appliedFacets.length === 0) {
								OTDCPWAssetsFacetsView.handleClearFilters(view);
							} else if (eventDetail.isLocalSearch) {
								OTDCPWAssetsFacetsView.performKeywordSearch(view, eventDetail);
							} else {
								OTDCPWAssetsFacetsView._removeFacet(view, eventDetail);
							}
						});
					}
				});
				this.bind("setup-finished",function(){
					//Set the assets view title same as subfolder selected - This will be called for first time
					//Title is set here because when sub-foldername is available, then title tag is not available
					$(".otdcpw-detail-assets-title ot-i18n").attr("ot-token",this.properties.folderName);
				});
			} );
	
	function loadSubFolderArea(){
		var parent = this.internalProperties.parentView.internalProperties.parentView.internalProperties.parentView;
		var assetsWrapper = this.internalProperties.parentView.internalProperties.parentView;
		var assetsView = assetsWrapper.getChildView(OTDCPWAssetsEditView);
		var childList = parent.internalProperties.childList;
		//Check if tab selected is Metadata
		if(parent.properties.OTDCPWDetailEditView_selected != "OTDCPWAssetsWrapperEdit" || this.properties.loaded){
			return;
		}
		this.properties.loaded = true;
		//ResultsView._initContent.apply(assetsView, assetsView.internalProperties.arguments);
		//If no subfolder then exit
		if(childList.length == 0){
			return;
		}		
		
		//Filter out assets from the childlist if any		
		
		// for(var i=0; i<childList.length; i++){
		// 	if(childList[i].type != OTDCPWWorkspaceManager.CONTAINER)
		// 		continue;
		// 	var navEl = getNavOption(childList[i].asset_id,childList[i].name,OTDCPWAssetsView.handleFolderSel);	
		// 	//By default first element will be active
		// 	if(isFirstEl){
		// 		isFirstEl = false;
		// 		var divEl = document.createElement("div");
		// 		divEl.setAttribute("class","otdcpw-preview-content");
		// 		navEl.setAttribute("class","otdcpw-detail-side-div otdcpw-detail-asset-folder-active");
		// 		this.properties.breadcrumb = { 'ids': [childList[i].asset_id], 'names': [childList[i].name]}
		// 		//Check if folder can contain assets
		// 		//OTDCPWAssetsView.setUPUploadAssets.call(this,parent);
		// 	}					
		// 	divEl.append(navEl);		
		// }
		// $(".ot-inspectorview-content").prepend(divEl);	
		var isFirstEl = true;

		for(var i=0; i<childList.length; i++){
			if(childList[i].type != OTDCPWWorkspaceManager.CONTAINER)
				continue;
			var navEl = getNavOption(childList[i].asset_id,childList[i].name,OTDCPWAssetsEditView.handleFolderSel);	
			if(isFirstEl){
				isFirstEl = false;
				var divEl = document.createElement("div");
				divEl.setAttribute("class","otdcpw-preview-content");
			}
			if(childList[i].asset_id == assetsView.properties.folderID){
				navEl.setAttribute("class","otdcpw-detail-side-div otdcpw-detail-asset-folder-active");
				assetsView.properties.breadcrumb = { 'ids': [childList[i].asset_id], 'names': [childList[i].name]}
			}					
			divEl.append(navEl);		
		}
		$(".otdcpw-asset-subfolder-view").empty();
		$(".otdcpw-asset-subfolder-view").append(divEl);	
		
	}
		
	function loadAssetsResultsArea(){
		let parent = this.internalProperties.parentView.internalProperties.parentView;
		//Check if tab selected is Metadata
		if(parent.properties.OTDCPWDetailEditView_selected != "OTDCPWAssetsWrapperEdit" || this.properties.loaded){
			return;
		}
		this.properties.loaded = true;
		ResultsView._initContent.apply(this, this.internalProperties.arguments);
	}
	function loadwithdelay(){
		var self = this;
		setTimeout(() => {
			loadAssetsResultsArea.call(self);
		}, 100);
	}

	OTDCPWAssetsEditView.handleFolderSel = function handleFolderSel(event){
		var oldActiveEl = $(".otdcpw-detail-asset-folder-active");
		oldActiveEl.removeClass("otdcpw-detail-asset-folder-active");
		event.currentTarget.setAttribute("class","otdcpw-detail-side-div otdcpw-detail-asset-folder-active");
		var folderView = otui.Views.containing(event.currentTarget);
		var wrapperView = folderView.internalProperties.parentView.internalProperties.parentView;
		var assetsView = wrapperView.getChildView(OTDCPWAssetsEditView);
		//Below are required to fetch assets - so set it with active folder id
		assetsView.properties.serviceData.nodeID = event.currentTarget.id;
		assetsView.properties.folderID = event.currentTarget.id;
		//Set breadcrumb which will be used as target folder by upload assets dialog
		assetsView.properties.breadcrumb = { 'ids': [event.currentTarget.id], 'names': [event.currentTarget.querySelector("span").textContent]};
		//Check if user has permission to upload -TODO

		//Set selected folder name as title of assets view
		if(event.currentTarget.innerText){
			assetsView.properties.folderName = event.currentTarget.innerText;
			$(".otdcpw-detail-assets-title ot-i18n").attr("ot-token",event.currentTarget.innerText);
		}else{
			$(".otdcpw-detail-assets-title ot-i18n").attr("ot-token","Assets");
		}
		//Revaluate upload assets button 
		document.querySelectorAll('ot-point[ot-lookup=OTDCPWDetailViewHeader]').forEach(function(point){
			point.reevaluate(); //This method calls setup method of this ot-point OTDCPWDetailViewHeader.setupUploadAsset
		});
		if(assetsView)
			assetsView.reload();
	}
    //Product Workspace Detail View - Security Policies screen
	function render() {
		var contentArea = this.contentArea()[0];
		//Check if user has edit access
		var parent = this.internalProperties.parentView;
		var permissions = parent.internalProperties.permissions;
		this.internalProperties.hasSecurityPermission = AssetDetailManager.getSecurityEditPermission(permissions);
		if (!this.internalProperties.hasSecurityPermission) {
			otui.empty(contentArea);
			contentArea.appendChild(this.getTemplate("noedit"));
		} else {
			var asset = this.internalProperties.asset;
			var securityPolicies = asset.security_policy_list;
			if(this.properties.edit) 
			{
				var userEditablePolicies = UserSecurityPolicyManager.getEditableSecurityPolicies();
				var assignedEditableList = [];
				for(var index in securityPolicies)
				{
					var id = securityPolicies[index].id;
					if(userEditablePolicies[id] && userEditablePolicies[id].id === id)
					{
						assignedEditableList.push(userEditablePolicies[id]);
					}
				}
				securityPolicies = assignedEditableList;
			}

			if (securityPolicies) {
				contentArea.querySelector("ot-security-policy").assignedPolicies = securityPolicies;				
			}
		}
		this.storedProperty("rendered", true);
	};

	function loadSecurityPolicies(parent, asset){
		//Check if active tab is security View else return
		if(parent.properties.OTDCPWDetailEditView_selected != "OTDCPWDetailSecurityEdit"){
			return;
		}
		this.internalProperty("asset",this.internalProperties.parentView.internalProperties.asset);
		render.call(this);
	}
	var OTDCPWDetailSecurityEdit = exports.OTDCPWDetailSecurityEdit =
    otui.define("OTDCPWDetailSecurityEdit",["withContent"],
        function(){
            this.properties = {
                'name' : 'OTDCPWDetailSecurityEdit', // Name of the view
                'title': otui.tr("Security Policies"),
                'type' : 'OTDCPWDetailSecurityEdit',
				'edit' : true
            };
			this._initContent = function _initContent(self, callback) {	
				this.internalProperty("asset",this.internalProperties.parentView.internalProperties.asset);	
				//this.internalProperty("hasSecurityPermission",true);//TODO
				var content = this.getTemplate(this.properties.edit ? "edit-content" : "content");
				callback(content);				
				if (this.internalProperties.asset)
					render.call(this);
					
			}
			this.bind("show", function() {
				if(this.storedProperty("rendered"))
				{
					var contentArea = this.contentArea();
					if(this.properties.edit)
					{
						var securityVisibleDiv = contentArea.find(".ot-security-visible");
						if(securityVisibleDiv.length > 0)
						{
							otui.OTMMHelpers.fixSecurityPolicyFiltersPosition(contentArea, contentArea);
						}
					}
				}
			});
	
			this.bind("attached-to-view", function() {
				var parentView = this.internalProperties.parentView;
				
				if (parentView)
					parentView.bind("got-asset", loadSecurityPolicies.bind(this));
			});

			this.validate = function validateSecurity() {
				var valid = true;		
				var contentArea = this.contentArea()[0];
				if (contentArea) {
					var policyArea = contentArea.querySelector("ot-security-policy")
					if (policyArea)
						valid = policyArea.isSecurityValid();
				}				
				return valid;
			};
	
			this.displayError = function displayError(validationMessage) {
				validationMessage = validationMessage || otui.tr("Please assign at least one security policy.");
				var notification = {'message' : validationMessage, 'status' : 'warning'};
				otui.NotificationManager.showNotification(notification);
			};

			this.gatherMetadata = function gatherSecurity() {
				var data = {};
				var contentArea = this.contentArea()[0];
				if (contentArea) {
					var policyArea = contentArea.querySelector("ot-security-policy")
					if (policyArea) {
						data = policyArea.collectModifiedSecurityList("folder", true);
					}
				}						
				return data;
			};
        } );
    
    var OTDCPWDetailEditView = exports.OTDCPWDetailEditView =
        otui.define("OTDCPWDetailEditView",['withTabs'], function() {
            this.properties = {
               'name' : 'OTDCPWDetailEditView', // Name of the view
			   'title': otui.tr("Product Workspace Detail View"),
			   'type': 'OTDCPWDetailEditView',
			   'appliedFacets':JSON.stringify({'facets':[]}),
			   'searchID' : '*'
			};
			
			//Variable to hold order of tabs
			this.hierarchy =
				[
					{ 'view': OTDCPWAssetsWrapperEdit },
					{ 'view': OTDCPWDetailMetadataEdit },
					{ 'view': OTDCPWDetailSecurityEdit }
				];
			
			this._initContent =
				// Note that initContent functions must always have a name.
				function initRecentView(self, placeContent) {	
					self.storedProperty("title", otui.tr("Product Workspace Detail Edit View"));									
					OTDCPWDetailEditView.nodeID = this.properties.nodeID || this.properties.resourceID;
					OTDCPWDetailEditView.editMode = this.properties.editMode;
					// get the template with the id="content"
					var template = this.getTemplate("content");
					//place the template data into the UI.
					placeContent(template);
					//Add child views to Assets wrapper tab
					var wrapperView = self.getChildView(OTDCPWAssetsWrapperEdit);
					wrapperView.addChildView(new OTDCPWAssetsSidebarEdit());
					wrapperView.addChildView(new OTDCPWAssetsEditView());
					lock.call(this, function(success) {
						if (!success)
							OTDCPWDetailEditView.handleCancel(undefined,self);
						else {
							getWorkspaceMetadata.call(self);
							getFolderChildren.call(self);
						}
					});
				};
			this.bind("detach", function() { 
				unlock.call(this)
			});
			this.watch("selected",function(tab)
			{
				if(tab == "OTDCPWDetailMetadataEdit" || tab == "OTDCPWDetailSecurityEdit"){		
					var el = $(".otdcpw-preview-content").detach();			
					if(!this.internalProperties.preview){
						this.internalProperties.preview = el;
					}
				}
				if(tab == "OTDCPWAssetsEditView"){
					$(".ot-inspectorview-content").prepend(this.internalProperties.preview);
				}

			});

    }); // END: OTDCPWDetailEditView definition block
	function getWorkspaceMetadata(){
		var self = this;
		if(OTDCPWDetailEditView.nodeID){			
			OTDCPWDetailMetadataEdit.assetID = OTDCPWDetailEditView.nodeID;
		}
		var data = {'assetID' : OTDCPWDetailMetadataEdit.assetID , 'unwrap' : true};
		//self.blockContent();
		otui.services.asset.read(data,
			function(asset, success) {
				self.unblockContent();
				if (!success) {
					var msg;
					if (asset.exception_body) {
						if (asset.exception_body.http_response_code == 404)
							msg = otui.tr("Either the workspace does not exist or you do not have permission to view the workspace. Please contact your administrator.")
						else
							msg = asset.exception_body.message;
					}
					if (msg) {
						otui.NotificationManager.showNotification({
							'message' : msg,
							'status' : 'error',
							'stayOpen' : true
						});
					}
				} else {								
					self.internalProperty("asset",asset);					
					//Display Model name
					var metadataTitle = otui.tr("Metadata");
					if (asset && asset.metadata_model_id)
						metadataTitle = otui.tr("Metadata: {0}", otui.MetadataModelManager.getModelDisplayName(asset.metadata_model_id));
					//Get metadata view
					var metadataView = self.getChildView(OTDCPWDetailMetadataEdit);
					metadataView.properties.title = metadataTitle;
					//Get Model Information
					metadataView.properties.modelInfo = otui.MetadataModelManager.getModelByName(asset.metadata_model_id);	
					//Store asset info for further use
					metadataView.properties.assetInfo = asset;	
					//Set workspace title
					$("input[class='otdcpw-workspacetitle-editor']").val(asset.name);
					//Set thumbnail if exists
					var url=asset.rendition_content && asset.rendition_content.thumbnail_content && asset.rendition_content.thumbnail_content.url;
					if(url)
						$("ot-rendition.otdcpw-asset-thumbnail-icon").attr("src",url);
					//Get permission info
					var permissions = [];
					if (asset.access_control_descriptor && asset.access_control_descriptor.permissions_map)
						permissions = asset.access_control_descriptor.permissions_map.entry || [];
					self.internalProperty("permissions", permissions);
					var fieldObject = otui.MetadataModelManager.rationaliseFieldObject({'id' : 'ARTESIA.FIELD.ASSET NAME'});
					var editPermission = AssetDetailManager.getMetadataEditPermission(permissions);
					var hasEditPermission = asset.type == "com.artesia.container.Container" || asset.content_sub_type == "CLIP" ? editPermission : editPermission && fieldObject && fieldObject.editable;
					self.internalProperties.hasEditPermission = hasEditPermission;
					//If no access route back to view mode
					if(!hasEditPermission || !otui.UserFETManager.isTokenAvailable("FOLDER.EDIT_PROPERTIES.SINGLE")){
						OTDCPWDetailEditView.handleCancel(undefined,self);
						return;
					}
					//Populate locked by information 
					setupLocked.call(self.contentArea()[0],asset);
					self.trigger("got-asset", asset);
				}	
			});
	}
	
	function setupLocked(resource){
		if(resource && resource.locked)
			{
			// TODO: Server response must return metadata_lock_state_user_id..(ART-35892).. Once it gets fixed please check against
			// metadata_lock_state_user_id instead of metadata_lock_state_user_name.
			this.querySelector(".otdcpw-folder-status").style.display = "flex";
			if(resource.metadata_lock_state_user_name === JSON.parse(sessionStorage.session).login_name)
				{
					this.querySelector("[ot-text]").textContent = otui.tr("Locked by me");
				}
			else
				{
				var self = this;
				UserDetailsManager.getUserDetailsByID(resource.metadata_lock_state_user_name, function(userDetails)
					{
					self.querySelector("[ot-text]").textContent = otui.tr("Locked by {0}", userDetails.name);
					});
				}
			}
	}
	function getFolderChildren(){
		var self = this;
		var data = {};
		var selectionContext = {
			'load_asset_content_info':true
		};
		data.selection = { 
			'data_load_request' : selectionContext
		};
		data.id = OTDCPWDetailEditView.nodeID;
		OTDCPWWorkspaceManager.readFolderChildren(data,function callback(response,success){
			if(!success){
				//TODO Handle error
			}else{
				self.internalProperties.childList = response;
				var wrapperView = self.getChildView(OTDCPWAssetsWrapperEdit);
				var assetsView = wrapperView.getChildView(OTDCPWAssetsEditView);
				//Set node ID as first folder
				var isFirstEl = true;
				for(var i=0; i<response.length; i++){
					if(response[i].type != OTDCPWWorkspaceManager.CONTAINER)
						continue;
					//By default first element will be active
					if(isFirstEl){
						isFirstEl = false;
						assetsView.properties.serviceData.nodeID = response[i] && response[i].asset_id;
						assetsView.properties.folderID = response[i] && response[i].asset_id;
						assetsView.properties.folderName = response[i] && response[i].name;//This will be used later to set title of assets view
					}			
				}				
				self.trigger("got-subfolderinfo");
			}
		});
	}
    OTDCPWDetailEditView.handleCancel = function handleCancel(event,self){
		if(event)
			var view = otui.Views.containing(event.currentTarget);
		view = view || self;
		var params = {};
        var tab;
		unlock.call(view,function(response){
			//Map the tab view selected from edit to view
			if(view.properties.OTDCPWDetailEditView_selected == "OTDCPWDetailMetadataEdit"){
				tab = "OTDCPWDetailMetadataView";
			}else
			if(view.properties.OTDCPWDetailEditView_selected == "OTDCPWAssetsWrapperEdit"){
				tab = "OTDCPWAssetsViewWrapper";
			}else
			if(view.properties.OTDCPWDetailEditView_selected == "OTDCPWDetailSecurityEdit"){
				tab = "OTDCPWDetailSecurityView";
			}
			params["OTDCPWDetailView_selected"] = tab;
			params["resourceID"] = view.properties.nodeID;
			view.callRoute("show",params,true);
		});     
	}

	OTDCPWDetailEditView.handleSave = function handleSave(event){
		var parentView = otui.Views.containing(event.currentTarget);
		//Remove if error class title
		$(".otdcpw-workspacetitle-editor").removeClass(".otdcpw-in-error");
		if(!validate.call(parentView)){
			return;
		}
		var metadata = gatherMetadata.call(parentView);
		if (!metadata)
		{
			OTDCPWDetailEditView.handleCancel(event,parentView);
		}
		else {
			var self = parentView;

			var assetType = "folder";

			otui.services[assetType].update({'assetID' : OTDCPWDetailEditView.nodeID, 'modifiedData' : metadata}, function(response, status, success) {
				// There may be scenarios where in the save was successful but post save either the asset may not exist
				// or the user may not have permission to view the asset. For instance, the current user may remove the
				// "view" permission security policy for himself.
				if (!success && status == 404)
				{
					success = true;
					self.properties.openNext = true;					
				}

				if (!success && response && response.exception_body) {
					var error = response.exception_body.message;
					otui.NotificationManager.showNotification({
						'message' : error,
						'status' : 'error',
						'stayOpen' : true
					});
					self.properties.openNext = false;
				}
				if(!self.properties.showNext)
					OTDCPWDetailEditView.handleCancel(event,self);
				else
				{
					// if(self.properties.openNext === undefined)
					// 	self.properties.openNext = true;
				}

				if (assetType === "folder") {
					FolderManager.clearCachedFolderDataForID(self.storedProperty("assetID"));
				}
			});	
		}
		
	}
	
	function validate(){
		var invalid = [];
		//Check title is not empty
		if(!$(".otdcpw-workspacetitle-editor").val()){
			$(".otdcpw-workspacetitle-editor").addClass("otdcpw-in-error");
			var error = otui.tr("Name is required");
			$(".otdcpw-error-msg").text(error);
			invalid.push(view);
		}
		var focusView = undefined;
			
		this.childViews.reduce(function(invalid, view) {

			if (view.internalProperties.loaded && view.validate) {
				var result = view.validate();
				if (result === false)
					{
					invalid.push(view);
					view.displayError();
					}
				else if (result.constructor === Object) {
					if (!result.valid) {
						invalid.push(view);
						// if (result.focus && !focusView) {
						// 	focusView = {'view' : view, 'element' : result.focus}
						// }
					}
				}
			}			
			return invalid;			
		}, invalid);
		
		if (invalid.length) {
			var view = focusView ? focusView.view : invalid[0];
			if (view && view !== this) {
				this.properties.selected = view.properties.name;
				if (focusView && focusView.element)
					focusView.element.scrollIntoView();
			}
		}
			
		return !(invalid.length);
	}

	function gatherMetadata(){
		var data = this.childViews.reduce(function(data, view) {
			if (view.internalProperties.loaded && view.gatherMetadata) {
				var viewData = view.gatherMetadata();
				if (viewData) {
					for (var name in viewData) {
						if (name in data && Array.isArray(data[name]))
							data[name].push.apply(data[name], viewData[name]);
						else
							data[name] = viewData[name];
					}
				}
			}
			return data;
		},{});
		var titleEl = $(".otdcpw-workspace-title");
		if (titleEl[0].children[0].dirty) {
			if (!data.metadata)
				data.metadata = [];
			generateValueforSave(titleEl[0], data.metadata, "ARTESIA.FIELD.ASSET NAME");
		}
		if (!Object.keys(data).length)
			return;
		
		var json = {};
		json['edited_folder'] = {'data' : data};
		return json;
	}

	function lock(callback) {
		if (this.internalProperties.lock || this.internalProperties.locking)
			callback(true);
		else {
			this.blockContent();
			var self = this;			
			this.internalProperties.locking = true;
			
			otui.services.asset.lock({'assetID' : this.properties.nodeID}, function(response, status, success) {
				delete self.internalProperties.locking;
				if (success)
					self.internalProperties.lock = response;
				else {
					var error = (response && response.exception_body) ? response.exception_body.message : otui.tr("Could not lock this asset.");
					otui.NotificationManager.showNotification({
						'message' : error,
						'status' : 'error',
						'stayOpen' : true
					});
				}
				
				self.unblockContent();
				callback(success);
			});
		}
	};
	
	function unlock(callback) {		
		if (!this.internalProperties.lock) {
			if (callback)
				callback(true);
		} else {
			delete this.internalProperties.lock;
			otui.services.asset.unlock({'assetID' : this.properties.nodeID}, function(response, status, success) {
				if (callback)
					callback(response);
			});
		}

	}

	OTDCPWDetailEditView.showVariantsDialog = function(data, event) {
		otui.services.asset.read(data,
			function (workspaceMetadata, success) {
				if (!success) {
					let msg;
					if (workspaceMetadata.exception_body) {
						if (workspaceMetadata.exception_body.http_response_code == 404)
							msg = otui.tr("Either the workspace does not exist or you do not have permission to view the asset. Please contact your administrator.")
						else
							msg = workspaceMetadata.exception_body.message;
					}
					if (msg) {
						otui.NotificationManager.showNotification({
							'message': msg,
							'status': 'error',
							'stayOpen': true
						});
					}
				} else {
					OTDCPWVariantsLookupView.show(data, workspaceMetadata, event);
				}
			});
	}

	OTDCPWDetailEditView.PWDetailEditRouter = function(req, routeParts, routeData){
		return {
			'name' : 'OTDCPWDetailEditView_' +  req.params.resourceID,
			'nodeID' : req.params.resourceID,
			'resourceID': req.params.resourceID,
			'editMode' : req.params.editMode || true
			};
	};

	OTDCPWDetailEditView.collapseAndExpandSidebar = function collapseAndExpandSidebar() {
		document.querySelector('ot-view-collapser').click();
	}

	OTDCPWDetailEditView.tabClickListener = function tabClickListener(event) {
		if (event.target.tagName === 'OT-VIEW-COLLAPSER') {
			if ($('.sidebar-expand-control-wrapper').length == 0) {
				let expandButton = $('<div class="sidebar-expand-control-wrapper" onclick="OTDCPWDetailView.collapseAndExpandSidebar()"> <span class="sidebar-expand-control-icon" title="Show left panel"><ot-i18n ot-as-title="" ot-token="Show left panel" parsed="true"></ot-i18n></span> </div>')
				$('.ot-left-toolbar-section').prepend(expandButton);
			} else {
				$('.sidebar-expand-control-wrapper').remove();
			}
		}
	}

	OTDCPWDetailEditView.ModernUIHeaderParameters = {'isModernResultsView': true, 'showBreadcrumbPanel': false};

	OTDCPWDetailEditView.route.base = "/:nodeID@:OTDCPWDetailEditView_selected?@:fromView?";
	//Here go and edit routes does the same job, but for inspector navigation back we need routes in some format like
	//It should have 1. Base url 2. go route and 3. open route
	OTDCPWDetailEditView.route.define("go",OTDCPWDetailEditView.route.as("otdcWorkspaceEdit" + OTDCPWDetailEditView.route.base,
		otui.withTemplate("main-with-actionbar"),  
		OTDCPWDetailEditView.route.to(OTDCPWDetailEditView.PWDetailEditRouter),
		{...OTDCPWDetailEditView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Workspace editor')}
	));

	OTDCPWDetailEditView.route.define("edit",OTDCPWDetailEditView.route.as("otdcpwDetailEdit" + "/:resourceID@:OTDCPWDetailEditView_selected?@:fromView?",
		otui.withTemplate("main-with-actionbar"),  
		OTDCPWDetailEditView.route.to(OTDCPWDetailEditView.PWDetailEditRouter),
		{...OTDCPWDetailEditView.ModernUIHeaderParameters, 'pageTitle': otui.tr('Workspace editor')}
	));

	// Then bind that route URL into the named route "open".
	OTDCPWDetailEditView.route.define("open", OTDCPWDetailEditView.route.use("go",[OTDCPWDetailEditView]));

	//Route for on changing tabs from Metadata to Asset or Security, URL gets updated
	OTDCPWDetailEditView.route.define("change-tab", function(routeName, params, viewProps)
	{		
		OTDCPWDetailEditView.route.update("go")(routeName, params, viewProps);
	});
	//Route for pagination
	OTDCPWAssetsEditView.route.define("change-page", function(routeName, params) { 
		this.properties.pageProperties = params.pageProperties;
	},)
	//Route for Asset Inspector View - View mode
	OTDCPWDetailEditView.route.define("open-contents",InspectorView.route.use("open"));
	//Route for Asset Inspector Edit View
	OTDCPWDetailEditView.route.define("edit-contents",InspectorEditView.route.use("open"));
	//Route for double click of folder
	OTDCPWAssetsEditView.route.define("open-folder",FolderResultsView.route.use("open"));
	//Logic to route back from Inspector View to the Detail view
	otui.ready(
		function(){
			//Logic to route back from Inspector View to the Detail view
			function linkToInspectorView(source)
				{
				/* This should be read as "Link FolderResultsView to ReviewTableView" by:
					* 1: Generating a linked "go" route for when the FolderResultsView is opened by the ReviewTableView.
					* 	The URL for this route is the URL for the source's "open" route, and the base URL for the InspectorView added.
					*  When the URL matches this, do the same actions as for the standard "go" route, but also give extra parameters in the form of the "options" object.
					*  Once this has been done, defining a route for the source which calls the "go" route of the InspectorView (Or any other route which uses "go" as a base) will follow this URL.
					* 2: Generating a linked "back" route for when the InspectorView was opened by the source. When that happens, the "open" route for the source is called.
					*/
				InspectorView.route.link(source, {'go' : 'open'}, {'back' : 'open'}, undefined, {'props_exclude_sanitization': ["breadcrumb"], 'pageTitle': otui.tr('Asset details') });
				InspectorEditView.route.link(source, {'go' : 'open'}, {'back' : 'open'}, undefined, {'props_exclude_sanitization': ["breadcrumb"], 'pageTitle': otui.tr('Asset editor') });
				};
			[OTDCPWDetailEditView].forEach(linkToInspectorView);
		}
	);
	//**Few routes for this view are defined in OTDCPWDetailView as there is some dependency on it
})(window);