/openspec-explore

# Overview

The Following Problem: I have an app that can track one's spending on fuel and calculate some stats. Another feature is that it can analyze the past entries and predict when prices will be lowest.

# The Problem

It only considers self recorded prices at the moment which greatly lessens the effect of the prediction model.

# Your Task

Research a way to get more data to improve the prediction model, keep in mind that it is a serverless mobile app and data can ony be stored to a limited degree because storage is limited on mobile devices.

------------------------------

# Q&A 1

  1. Target Geography: What country/region is app targeting? (Determines if official open API exists 
  like Germany/Austria/France).

  The app is target towards upper-austria.
  
  2. API Key Handling: Should app ship default read-only community key, or let user enter key, or    
  rely on static country-level baseline curves?

  There should be a default api key with the option to enter your own to avoid potential reate limit issues. I want the app to find a local average and adjust it with data the user collects and data thats coming in from the API.
  
  3. Data Source Preference: Hybrid approach (static prior curve + localized open API fetch for      
  favorite stations)?

  Yes
