import { NgModule } from '@angular/core'

import { SharedModule } from '../shared/shared.module'
import { CustomScriptsComponent } from './custom-scripts.component'
import { CustomScriptsRoutingModule } from './custom-scripts.routing.module'

@NgModule({
  imports: [
    SharedModule,
    CustomScriptsRoutingModule,
  ],
  declarations: [
    CustomScriptsComponent,
  ],
  exports: []
})
export class CustomScriptsModule { }
