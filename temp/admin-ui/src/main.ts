import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.module';

import 'codemirror/mode/xml/xml.js';
import 'codemirror/mode/properties/properties.js';
import 'codemirror/mode/javascript/javascript.js';
import 'codemirror/addon/fold/xml-fold.js';
import 'codemirror/addon/edit/matchtags.js';
import 'codemirror/addon/fold/foldcode.js';
import 'codemirror/addon/fold/foldgutter.js';

bootstrapApplication(AppComponent, appConfig)
  .catch(err => console.error(err));
